package com.potatosmp.bounty;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.config.ConfigManager;
import com.potatosmp.database.BountyContribution;
import com.potatosmp.database.BountyRecord;
import com.potatosmp.database.DatabaseManager;
import com.potatosmp.database.PlayerProfile;
import com.potatosmp.database.TransactionType;
import com.potatosmp.economy.EconomyService;
import com.potatosmp.util.CoreLogic;
import com.potatosmp.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public final class BountyService {

    public record PlaceResult(boolean success, String message, long newTotal) {}

    private final PotatoSMPPlugin plugin;
    private final ConfigManager configManager;
    private final DatabaseManager db;
    private final EconomyService economy;
    private BukkitTask expirationTask;

    public BountyService(PotatoSMPPlugin plugin, ConfigManager configManager, DatabaseManager db, EconomyService economy) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.db = db;
        this.economy = economy;
    }

    public void start() {
        stop();
        // Check expired bounties every 60 seconds
        expirationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processExpiredBounties, 20L * 30L, 20L * 60L);
    }

    public void stop() {
        if (expirationTask != null) {
            expirationTask.cancel();
            expirationTask = null;
        }
    }

    public PlaceResult placeOrIncreaseBounty(Player placer, UUID targetUuid, String targetName, long amount) {
        if (!configManager.isFeatureEnabled("bounties")) {
            return new PlaceResult(false, "Bounty system is currently disabled.", 0L);
        }
        boolean allowSelf = configManager.getConfig().getBoolean("bounty.allow-self-bounty", false);
        if (!allowSelf && placer.getUniqueId().equals(targetUuid)) {
            placer.sendMessage(configManager.msg("bounty.self-bounty"));
            return new PlaceResult(false, "Cannot place a bounty on yourself.", 0L);
        }

        long minAmount = configManager.getConfig().getLong("bounty.min-amount", 100L);
        long maxAmount = configManager.getConfig().getLong("bounty.max-amount", 1000000L);
        if (amount < minAmount || amount > maxAmount) {
            return new PlaceResult(false, "Bounty amount must be between "
                    + TextUtil.formatCredits(minAmount) + " and " + TextUtil.formatCredits(maxAmount) + " Credits.", 0L);
        }

        ReentrantLock lock = economy.lockFor(placer.getUniqueId());
        lock.lock();
        try {
            PlayerProfile placerProfile = economy.getOrLoadProfile(placer.getUniqueId(), placer.getName());
            if (placerProfile.getCredits() < amount) {
                return new PlaceResult(false, "Insufficient credits (have "
                        + TextUtil.formatCredits(placerProfile.getCredits()) + ").", 0L);
            }

            long now = System.currentTimeMillis();
            long expHours = Math.max(1L, configManager.getConfig().getLong("bounty.expiration-hours", 72L));
            long expiresAt = now + (expHours * 3600L * 1000L);

            long updatedTotal = db.executeInTransaction(conn -> {
                placerProfile.setCredits(placerProfile.getCredits() - amount);
                db.savePlayerInternal(conn, placerProfile);
                db.insertTransactionInternal(conn, placer.getUniqueId(), TransactionType.BOUNTY_PLACE,
                        -amount, 0, "Bounty on " + targetName);

                Optional<BountyRecord> existing = db.getActiveBountyByTargetInternal(conn, targetUuid);
                long bountyId;
                long newTotal;
                if (existing.isPresent()) {
                    bountyId = existing.get().id();
                    db.addBountyAmountInternal(conn, bountyId, amount, expiresAt);
                    newTotal = existing.get().totalAmount() + amount;
                } else {
                    bountyId = db.createBountyInternal(conn, targetUuid, targetName, amount, now, expiresAt);
                    newTotal = amount;
                }
                db.addBountyContributionInternal(conn, bountyId, placer.getUniqueId(), placer.getName(), amount, now);
                return newTotal;
            });

            if (configManager.getConfig().getBoolean("bounty.broadcast-on-place", true)) {
                Bukkit.broadcast(configManager.msg("bounty.placed", Map.of(
                        "placer", placer.getName(),
                        "amount", TextUtil.formatCredits(amount),
                        "target", targetName,
                        "total", TextUtil.formatCredits(updatedTotal)
                )));
            }

            Player onlineTarget = Bukkit.getPlayer(targetUuid);
            if (onlineTarget != null && onlineTarget.isOnline()) {
                onlineTarget.sendMessage(configManager.msg("bounty.target-notified", Map.of(
                        "placer", placer.getName(),
                        "amount", TextUtil.formatCredits(amount)
                )));
                onlineTarget.playSound(onlineTarget.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 0.7f);
            }
            placer.playSound(placer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f);
            return new PlaceResult(true, "Bounty placed!", updatedTotal);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to place bounty: " + e.getMessage());
            return new PlaceResult(false, "Database error while placing bounty.", 0L);
        } finally {
            lock.unlock();
        }
    }

    public void handleKill(Player killer, Player victim) {
        if (!configManager.isFeatureEnabled("bounties")) {
            return;
        }
        if (killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        Optional<BountyRecord> activeOpt;
        try {
            activeOpt = db.getActiveBountyByTarget(victim.getUniqueId());
        } catch (SQLException e) {
            plugin.getLogger().warning("Error checking bounty on kill: " + e.getMessage());
            return;
        }
        if (activeOpt.isEmpty()) {
            return;
        }

        BountyRecord bounty = activeOpt.get();
        long now = System.currentTimeMillis();
        if (bounty.expiresAt() <= now) {
            processExpiredBounties();
            return;
        }

        ReentrantLock lock = economy.lockFor(killer.getUniqueId());
        lock.lock();
        try {
            boolean preventContributor = configManager.getConfig().getBoolean("bounty.prevent-contributor-claim", true);
            long cooldownMinutes = Math.max(0L, configManager.getConfig().getLong("bounty.anti-farm-cooldown-minutes", 60L));
            int maxClaimsPerDay = Math.max(0, configManager.getConfig().getInt("bounty.max-claims-per-pair-per-day", 2));

            if (preventContributor) {
                List<BountyContribution> contribs = db.getBountyContributions(bounty.id());
                boolean isContributor = contribs.stream().anyMatch(c -> c.contributorUuid().equals(killer.getUniqueId()));
                if (isContributor) {
                    killer.sendMessage(configManager.msg("bounty.contributor-blocked"));
                    return;
                }
            }

            PlayerProfile killerProfile = economy.getOrLoadProfile(killer.getUniqueId(), killer.getName());
            long claimedAmount = db.executeInTransaction(conn -> {
                Optional<BountyRecord> fresh = db.getActiveBountyByTargetInternal(conn, victim.getUniqueId());
                if (fresh.isEmpty() || fresh.get().id() != bounty.id()) {
                    return 0L;
                }

                long lastPairClaim = db.getLastPairClaimTimeInternal(conn, killer.getUniqueId(), victim.getUniqueId());
                int claims24h = db.getPairClaimsSinceInternal(conn, killer.getUniqueId(), victim.getUniqueId(),
                        now - 86_400_000L);
                if (!CoreLogic.isBountyClaimAllowed(now, lastPairClaim, cooldownMinutes * 60_000L, claims24h, maxClaimsPerDay)) {
                    return -1L;
                }

                long payout = fresh.get().totalAmount();
                db.markBountyClaimedInternal(conn, fresh.get().id(), killer.getUniqueId(), killer.getName(), now);
                db.recordPairClaimInternal(conn, killer.getUniqueId(), victim.getUniqueId(), now);

                killerProfile.setCredits(killerProfile.getCredits() + payout);
                killerProfile.incrementBountiesClaimedCount();
                killerProfile.addBountyCreditsEarned(payout);
                db.savePlayerInternal(conn, killerProfile);
                db.insertTransactionInternal(conn, killer.getUniqueId(), TransactionType.BOUNTY_CLAIM,
                        payout, 0, "Claimed bounty on " + victim.getName());
                return payout;
            });

            if (claimedAmount == -1L) {
                killer.sendMessage(configManager.msg("bounty.anti-farm-blocked", Map.of(
                        "killer", killer.getName(),
                        "victim", victim.getName()
                )));
                return;
            }
            if (claimedAmount <= 0L) {
                return;
            }

            killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.15f);
            if (configManager.getConfig().getBoolean("bounty.broadcast-on-claim", true)) {
                Bukkit.broadcast(configManager.msg("bounty.claimed", Map.of(
                        "killer", killer.getName(),
                        "victim", victim.getName(),
                        "amount", TextUtil.formatCredits(claimedAmount)
                )));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed claiming bounty: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public void processExpiredBounties() {
        long now = System.currentTimeMillis();
        try {
            List<BountyRecord> expired = db.executeLocked(conn -> db.getExpiredActiveBountiesInternal(conn, now));
            for (BountyRecord bounty : expired) {
                List<BountyContribution> contribs = db.getBountyContributions(bounty.id());
                db.executeInTransaction(conn -> {
                    db.markBountyStatusInternal(conn, bounty.id(), "EXPIRED");
                    return null;
                });
                for (BountyContribution c : contribs) {
                    economy.addCredits(c.contributorUuid(), c.contributorName(), c.amount(),
                            TransactionType.BOUNTY_REFUND, "Expired bounty on " + bounty.targetName());
                    Player online = Bukkit.getPlayer(c.contributorUuid());
                    if (online != null && online.isOnline()) {
                        online.sendMessage(configManager.msg("bounty.expired-refund", Map.of(
                                "amount", TextUtil.formatCredits(c.amount()),
                                "target", bounty.targetName()
                        )));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error expiring bounties: " + e.getMessage());
        }
    }

    public Optional<BountyRecord> getActiveBounty(UUID targetUuid) {
        try {
            return db.getActiveBountyByTarget(targetUuid);
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public List<BountyRecord> getActiveBounties(int limit) {
        try {
            return db.getActiveBounties(limit);
        } catch (SQLException e) {
            return List.of();
        }
    }

    public List<BountyRecord> getPlacedBounties(UUID playerUuid, int limit) {
        try {
            return db.getBountiesPlacedBy(playerUuid, limit);
        } catch (SQLException e) {
            return List.of();
        }
    }

    public List<BountyRecord> getCompletedBounties(int limit) {
        try {
            return db.getCompletedBounties(limit);
        } catch (SQLException e) {
            return List.of();
        }
    }

    public OfflinePlayer resolveOfflineTarget(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        Optional<PlayerProfile> known = economy.findByName(name);
        return known.map(p -> Bukkit.getOfflinePlayer(p.getUuid())).orElse(null);
    }
}
