package com.potatosmp.economy;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.config.ConfigManager;
import com.potatosmp.database.DatabaseManager;
import com.potatosmp.database.PlayerProfile;
import com.potatosmp.database.TransactionRecord;
import com.potatosmp.database.TransactionType;
import com.potatosmp.items.ItemFactory;
import com.potatosmp.items.PotatoItemType;
import com.potatosmp.util.CoreLogic;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class EconomyService {

    public record DepositPreview(int regularPotatoes, int goldenPotatoes, int poisonousPotatoes, long estimatedCredits) {
        public int totalItems() {
            return regularPotatoes + goldenPotatoes + poisonousPotatoes;
        }
    }

    public record DepositResult(boolean success, int regularPotatoes, int goldenPotatoes, int poisonousPotatoes,
                                long creditsAwarded, long newBalance, String errorReason) {}

    private final PotatoSMPPlugin plugin;
    private final ConfigManager configManager;
    private final DatabaseManager db;
    private final MarketService market;
    private final Map<UUID, PlayerProfile> profileCache = new ConcurrentHashMap<>();
    private final Map<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();

    public EconomyService(PotatoSMPPlugin plugin, ConfigManager configManager, DatabaseManager db, MarketService market) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.db = db;
        this.market = market;
    }

    public ReentrantLock lockFor(UUID uuid) {
        return playerLocks.computeIfAbsent(uuid, ignored -> new ReentrantLock(true));
    }

    public PlayerProfile getOrLoadProfile(UUID uuid, String knownName) {
        PlayerProfile cached = profileCache.get(uuid);
        if (cached != null) {
            if (knownName != null && !knownName.equals(cached.getLastKnownName())) {
                cached.setLastKnownName(knownName);
            }
            return cached;
        }
        long startCredits = configManager.getConfig().getLong("economy.starting-credits", 0L);
        try {
            PlayerProfile loaded = db.loadOrCreatePlayer(uuid, knownName, startCredits);
            profileCache.put(uuid, loaded);
            return loaded;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load profile for " + uuid + ": " + e.getMessage());
            return new PlayerProfile(uuid, knownName == null ? "Unknown" : knownName, 0L, 0L, 0L, 0, 0, 0L, true);
        }
    }

    public Optional<PlayerProfile> findByName(String name) {
        for (PlayerProfile profile : profileCache.values()) {
            if (profile.getLastKnownName().equalsIgnoreCase(name)) {
                return Optional.of(profile);
            }
        }
        try {
            Optional<PlayerProfile> loaded = db.findPlayerByName(name);
            loaded.ifPresent(p -> profileCache.put(p.getUuid(), p));
            return loaded;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to find player by name " + name + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public void unloadProfile(UUID uuid) {
        PlayerProfile profile = profileCache.remove(uuid);
        if (profile != null) {
            try {
                db.savePlayer(profile);
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to flush profile on quit for " + uuid + ": " + e.getMessage());
            }
        }
    }

    public void flushAll() {
        for (PlayerProfile profile : profileCache.values()) {
            try {
                db.savePlayer(profile);
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to flush profile for " + profile.getUuid() + ": " + e.getMessage());
            }
        }
    }

    public long getBalance(UUID uuid) {
        return getOrLoadProfile(uuid, null).getCredits();
    }

    public boolean addCredits(UUID uuid, String name, long amount, TransactionType type, String details) {
        if (amount <= 0) {
            return false;
        }
        ReentrantLock lock = lockFor(uuid);
        lock.lock();
        try {
            PlayerProfile profile = getOrLoadProfile(uuid, name);
            long updated = safeAdd(profile.getCredits(), amount);
            db.executeInTransaction(conn -> {
                profile.setCredits(updated);
                db.savePlayerInternal(conn, profile);
                db.insertTransactionInternal(conn, uuid, type, amount, 0, details);
                return null;
            });
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed addCredits for " + uuid + ": " + e.getMessage());
            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean withdrawCredits(UUID uuid, String name, long amount, TransactionType type, String details) {
        if (amount <= 0) {
            return false;
        }
        ReentrantLock lock = lockFor(uuid);
        lock.lock();
        try {
            PlayerProfile profile = getOrLoadProfile(uuid, name);
            if (profile.getCredits() < amount) {
                return false;
            }
            long updated = profile.getCredits() - amount;
            db.executeInTransaction(conn -> {
                profile.setCredits(updated);
                db.savePlayerInternal(conn, profile);
                db.insertTransactionInternal(conn, uuid, type, -amount, 0, details);
                return null;
            });
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed withdrawCredits for " + uuid + ": " + e.getMessage());
            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean setCredits(UUID uuid, String name, long newAmount, String details) {
        if (newAmount < 0) {
            return false;
        }
        ReentrantLock lock = lockFor(uuid);
        lock.lock();
        try {
            PlayerProfile profile = getOrLoadProfile(uuid, name);
            long delta = newAmount - profile.getCredits();
            db.executeInTransaction(conn -> {
                profile.setCredits(newAmount);
                db.savePlayerInternal(conn, profile);
                db.insertTransactionInternal(conn, uuid, TransactionType.ADMIN_ADJUST, delta, 0, details);
                return null;
            });
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed setCredits for " + uuid + ": " + e.getMessage());
            return false;
        } finally {
            lock.unlock();
        }
    }

    public DepositPreview previewInventoryDeposit(Player player, int maxRegularLimit) {
        boolean allowGolden = configManager.isFeatureEnabled("golden-potatoes");
        boolean allowPoison = configManager.getConfig().getBoolean("economy.allow-poisonous-potato-deposit", false);

        int regular = 0;
        int golden = 0;
        int poisonous = 0;

        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (allowGolden && ItemFactory.getItemType(item) == PotatoItemType.GOLDEN_POTATO) {
                golden += item.getAmount();
            } else if (item.getType() == Material.POTATO && !ItemFactory.isPotatoItem(item)) {
                regular += item.getAmount();
            } else if (allowPoison && item.getType() == Material.POISONOUS_POTATO && !ItemFactory.isPotatoItem(item)) {
                poisonous += item.getAmount();
            }
        }

        if (maxRegularLimit >= 0) {
            regular = Math.min(regular, maxRegularLimit);
            golden = 0;
            poisonous = 0;
        }

        long credits = CoreLogic.calculateDepositCredits(
                regular, market.getRegularPotatoRate(),
                golden, market.getGoldenPotatoRate(),
                poisonous, allowPoison ? market.getPoisonousPotatoRate() : 0.0
        );
        return new DepositPreview(regular, golden, poisonous, credits);
    }

    /**
     * Atomically inspects live inventory, removes eligible potatoes, credits player,
     * logs ledger entry, updates weekly competition & community monument.
     */
    public DepositResult executeDeposit(Player player, int maxRegularLimit) {
        UUID uuid = player.getUniqueId();
        ReentrantLock lock = lockFor(uuid);
        lock.lock();
        try {
            boolean allowGolden = configManager.isFeatureEnabled("golden-potatoes");
            boolean allowPoison = configManager.getConfig().getBoolean("economy.allow-poisonous-potato-deposit", false);

            DepositPreview live = previewInventoryDeposit(player, maxRegularLimit);
            if (live.totalItems() <= 0 || live.estimatedCredits() <= 0) {
                return new DepositResult(false, 0, 0, 0, 0L, getBalance(uuid), "No eligible potatoes in inventory.");
            }

            // Re-scan and deduct exact counts from storage slots (never armor/offhand exploit)
            PlayerInventory inv = player.getInventory();
            ItemStack[] contents = inv.getStorageContents();
            int remRegular = live.regularPotatoes();
            int remGolden = live.goldenPotatoes();
            int remPoison = live.poisonousPotatoes();

            int removedRegular = 0;
            int removedGolden = 0;
            int removedPoison = 0;

            for (int i = 0; i < contents.length; i++) {
                ItemStack stack = contents[i];
                if (stack == null || stack.getType().isAir()) {
                    continue;
                }
                if (remGolden > 0 && allowGolden && ItemFactory.getItemType(stack) == PotatoItemType.GOLDEN_POTATO) {
                    int take = Math.min(remGolden, stack.getAmount());
                    remGolden -= take;
                    removedGolden += take;
                    if (take >= stack.getAmount()) {
                        contents[i] = null;
                    } else {
                        stack.setAmount(stack.getAmount() - take);
                    }
                } else if (remRegular > 0 && stack.getType() == Material.POTATO && !ItemFactory.isPotatoItem(stack)) {
                    int take = Math.min(remRegular, stack.getAmount());
                    remRegular -= take;
                    removedRegular += take;
                    if (take >= stack.getAmount()) {
                        contents[i] = null;
                    } else {
                        stack.setAmount(stack.getAmount() - take);
                    }
                } else if (remPoison > 0 && allowPoison && stack.getType() == Material.POISONOUS_POTATO && !ItemFactory.isPotatoItem(stack)) {
                    int take = Math.min(remPoison, stack.getAmount());
                    remPoison -= take;
                    removedPoison += take;
                    if (take >= stack.getAmount()) {
                        contents[i] = null;
                    } else {
                        stack.setAmount(stack.getAmount() - take);
                    }
                }
            }

            int totalRemoved = removedRegular + removedGolden + removedPoison;
            if (totalRemoved <= 0) {
                return new DepositResult(false, 0, 0, 0, 0L, getBalance(uuid), "No eligible potatoes found.");
            }

            long creditsEarned = CoreLogic.calculateDepositCredits(
                    removedRegular, market.getRegularPotatoRate(),
                    removedGolden, market.getGoldenPotatoRate(),
                    removedPoison, allowPoison ? market.getPoisonousPotatoRate() : 0.0
            );
            if (creditsEarned <= 0) {
                return new DepositResult(false, 0, 0, 0, 0L, getBalance(uuid), "Deposit value rounded to 0 credits.");
            }

            // Apply inventory removal on main thread (we are always called from Bukkit GUI/command on main thread)
            inv.setStorageContents(contents);

            PlayerProfile profile = getOrLoadProfile(uuid, player.getName());
            long newCredits = safeAdd(profile.getCredits(), creditsEarned);
            long totalPotatoStat = removedRegular + removedPoison + (removedGolden * 10L);

            long weekId = -1L;
            if (configManager.isFeatureEnabled("weekly-competition")) {
                weekId = plugin.getCompetitionService().getCurrentWeekId();
            }
            final long activeWeekId = weekId;
            final int finalTotalRemoved = totalRemoved;
            final int finalRemovedRegular = removedRegular;
            final int finalRemovedGolden = removedGolden;
            final int finalRemovedPoison = removedPoison;

            long updatedMonumentTotal = db.executeInTransaction(conn -> {
                profile.setCredits(newCredits);
                profile.addLifetimePotatoes(totalPotatoStat);
                if (finalRemovedGolden > 0) {
                    profile.addLifetimeGoldenPotatoes(finalRemovedGolden);
                }
                db.savePlayerInternal(conn, profile);
                String details = "Deposited " + finalRemovedRegular + " potatoes"
                        + (finalRemovedGolden > 0 ? ", " + finalRemovedGolden + " golden" : "")
                        + (finalRemovedPoison > 0 ? ", " + finalRemovedPoison + " poisonous" : "");
                db.insertTransactionInternal(conn, uuid, TransactionType.DEPOSIT, creditsEarned, finalTotalRemoved, details);

                if (activeWeekId > 0) {
                    db.addWeeklyDepositInternal(conn, activeWeekId, uuid, player.getName(), totalPotatoStat);
                }
                return db.addMonumentPotatoesInternal(conn, totalPotatoStat);
            });

            if (configManager.isFeatureEnabled("community-monument")) {
                plugin.getMonumentService().onPotatoesDeposited(updatedMonumentTotal);
            }

            return new DepositResult(true, removedRegular, removedGolden, removedPoison, creditsEarned, newCredits, null);
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL error during potato deposit for " + uuid + ": " + e.getMessage());
            return new DepositResult(false, 0, 0, 0, 0L, getBalance(uuid), "Database error during deposit.");
        } finally {
            lock.unlock();
        }
    }

    public List<TransactionRecord> getRecentTransactions(UUID uuid, int limit) {
        try {
            return db.getTransactions(uuid, limit);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load transactions for " + uuid + ": " + e.getMessage());
            return List.of();
        }
    }

    public List<PlayerProfile> getLeaderboard(String column, int limit) {
        try {
            return db.getTopPlayers(column, limit);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch leaderboard (" + column + "): " + e.getMessage());
            return List.of();
        }
    }

    private long safeAdd(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }
}
