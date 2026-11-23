package com.potatosmp.trade;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.config.ConfigManager;
import com.potatosmp.database.DatabaseManager;
import com.potatosmp.database.PlayerProfile;
import com.potatosmp.database.TransactionType;
import com.potatosmp.economy.EconomyService;
import com.potatosmp.gui.TradeMenu;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class TradeService {

    public record TradeRequest(UUID sender, UUID target, long expiresAt) {}

    public static final class TradeSession {
        public final UUID playerA;
        public final UUID playerB;
        public final ItemStack[] offerA = new ItemStack[12];
        public final ItemStack[] offerB = new ItemStack[12];
        public long creditsA = 0L;
        public long creditsB = 0L;
        public boolean readyA = false;
        public boolean readyB = false;
        public int countdown = -1;
        public BukkitTask countdownTask;
        public TradeMenu menuA;
        public TradeMenu menuB;
        public boolean finished = false;

        public TradeSession(UUID playerA, UUID playerB) {
            this.playerA = playerA;
            this.playerB = playerB;
        }

        public boolean isA(UUID uuid) {
            return playerA.equals(uuid);
        }

        public UUID partnerOf(UUID uuid) {
            return isA(uuid) ? playerB : playerA;
        }
    }

    private final PotatoSMPPlugin plugin;
    private final ConfigManager configManager;
    private final DatabaseManager db;
    private final EconomyService economy;
    private final Map<UUID, TradeRequest> incomingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, TradeSession> activeSessions = new ConcurrentHashMap<>();

    public TradeService(PotatoSMPPlugin plugin, ConfigManager configManager, DatabaseManager db, EconomyService economy) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.db = db;
        this.economy = economy;
    }

    public void sendRequest(Player sender, Player target) {
        if (!configManager.isFeatureEnabled("player-trading")) {
            sender.sendMessage(configManager.msg("general.feature-disabled"));
            return;
        }
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(configManager.msg("general.invalid-amount"));
            return;
        }
        if (activeSessions.containsKey(sender.getUniqueId()) || activeSessions.containsKey(target.getUniqueId())) {
            sender.sendMessage(configManager.msg("trade.cancelled"));
            return;
        }
        double maxDist = configManager.getConfig().getDouble("trading.max-distance-blocks", -1);
        if (maxDist > 0) {
            if (!sender.getWorld().equals(target.getWorld())
                    || sender.getLocation().distanceSquared(target.getLocation()) > maxDist * maxDist) {
                sender.sendMessage(configManager.msg("economy.bank-too-far", Map.of("coords", target.getName())));
                return;
            }
        }
        int timeout = Math.max(10, configManager.getConfig().getInt("trading.request-timeout-seconds", 45));
        long expires = System.currentTimeMillis() + (timeout * 1000L);
        incomingRequests.put(target.getUniqueId(), new TradeRequest(sender.getUniqueId(), target.getUniqueId(), expires));
        sender.sendMessage(configManager.msg("trade.sent", Map.of(
                "target", target.getName(),
                "seconds", String.valueOf(timeout)
        )));
        target.sendMessage(configManager.msg("trade.received", Map.of("sender", sender.getName())));
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
    }

    public void acceptRequest(Player target, String senderName) {
        TradeRequest req = incomingRequests.get(target.getUniqueId());
        if (req == null || req.expiresAt() < System.currentTimeMillis()) {
            incomingRequests.remove(target.getUniqueId());
            target.sendMessage(configManager.msg("trade.declined"));
            return;
        }
        Player sender = Bukkit.getPlayer(req.sender());
        if (sender == null || !sender.isOnline() || (senderName != null && !sender.getName().equalsIgnoreCase(senderName))) {
            target.sendMessage(configManager.msg("general.player-not-found", Map.of("player", senderName == null ? "?" : senderName)));
            return;
        }
        incomingRequests.remove(target.getUniqueId());
        if (activeSessions.containsKey(sender.getUniqueId()) || activeSessions.containsKey(target.getUniqueId())) {
            return;
        }

        TradeSession session = new TradeSession(sender.getUniqueId(), target.getUniqueId());
        activeSessions.put(sender.getUniqueId(), session);
        activeSessions.put(target.getUniqueId(), session);

        session.menuA = new TradeMenu(plugin, sender, session);
        session.menuB = new TradeMenu(plugin, target, session);
        session.menuA.open();
        session.menuB.open();
    }

    public void declineRequest(Player target) {
        TradeRequest req = incomingRequests.remove(target.getUniqueId());
        target.sendMessage(configManager.msg("trade.declined"));
        if (req != null) {
            Player sender = Bukkit.getPlayer(req.sender());
            if (sender != null && sender.isOnline()) {
                sender.sendMessage(configManager.msg("trade.declined"));
            }
        }
    }

    public TradeSession getSession(UUID uuid) {
        return activeSessions.get(uuid);
    }

    public void onPlayerQuit(UUID uuid) {
        incomingRequests.remove(uuid);
        TradeSession session = activeSessions.get(uuid);
        if (session != null) {
            cancelTrade(session, "trade.cancelled");
        }
    }

    public void resetReady(TradeSession session) {
        session.readyA = false;
        session.readyB = false;
        session.countdown = -1;
        if (session.countdownTask != null) {
            session.countdownTask.cancel();
            session.countdownTask = null;
        }
        refreshBoth(session);
    }

    public void toggleReady(Player player, TradeSession session) {
        if (session.finished) {
            return;
        }
        if (session.isA(player.getUniqueId())) {
            session.readyA = !session.readyA;
        } else {
            session.readyB = !session.readyB;
        }
        if (!session.readyA || !session.readyB) {
            session.countdown = -1;
            if (session.countdownTask != null) {
                session.countdownTask.cancel();
                session.countdownTask = null;
            }
            refreshBoth(session);
            return;
        }

        session.countdown = Math.max(1, configManager.getConfig().getInt("trading.confirm-countdown-seconds", 3));
        refreshBoth(session);
        session.countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (session.finished || !session.readyA || !session.readyB) {
                if (session.countdownTask != null) {
                    session.countdownTask.cancel();
                    session.countdownTask = null;
                }
                return;
            }
            session.countdown--;
            if (session.countdown <= 0) {
                if (session.countdownTask != null) {
                    session.countdownTask.cancel();
                    session.countdownTask = null;
                }
                completeTrade(session);
            } else {
                refreshBoth(session);
            }
        }, 20L, 20L);
    }

    public void refreshBoth(TradeSession session) {
        if (session.menuA != null) {
            session.menuA.render();
        }
        if (session.menuB != null) {
            session.menuB.render();
        }
    }

    public void cancelTrade(TradeSession session, String reasonKey) {
        if (session == null || session.finished) {
            return;
        }
        session.finished = true;
        if (session.countdownTask != null) {
            session.countdownTask.cancel();
            session.countdownTask = null;
        }
        activeSessions.remove(session.playerA);
        activeSessions.remove(session.playerB);

        Player pA = Bukkit.getPlayer(session.playerA);
        Player pB = Bukkit.getPlayer(session.playerB);

        returnItems(pA, session.offerA);
        returnItems(pB, session.offerB);

        if (pA != null && pA.isOnline()) {
            pA.closeInventory();
            pA.sendMessage(configManager.msg(reasonKey));
            pA.playSound(pA.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
        }
        if (pB != null && pB.isOnline()) {
            pB.closeInventory();
            pB.sendMessage(configManager.msg(reasonKey));
            pB.playSound(pB.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
        }
    }

    private void completeTrade(TradeSession session) {
        if (session.finished) {
            return;
        }
        Player pA = Bukkit.getPlayer(session.playerA);
        Player pB = Bukkit.getPlayer(session.playerB);
        if (pA == null || !pA.isOnline() || pB == null || !pB.isOnline()) {
            cancelTrade(session, "trade.cancelled");
            return;
        }

        // Order locks canonically by UUID to prevent deadlocks
        UUID firstUuid = session.playerA.compareTo(session.playerB) <= 0 ? session.playerA : session.playerB;
        UUID secondUuid = firstUuid.equals(session.playerA) ? session.playerB : session.playerA;
        ReentrantLock lock1 = economy.lockFor(firstUuid);
        ReentrantLock lock2 = economy.lockFor(secondUuid);
        lock1.lock();
        lock2.lock();
        try {
            if (session.finished) {
                return;
            }
            PlayerProfile profA = economy.getOrLoadProfile(session.playerA, pA.getName());
            PlayerProfile profB = economy.getOrLoadProfile(session.playerB, pB.getName());

            if (profA.getCredits() < session.creditsA || profB.getCredits() < session.creditsB) {
                cancelTrade(session, "trade.cancelled");
                return;
            }

            List<ItemStack> toB = collectNonEmpty(session.offerA);
            List<ItemStack> toA = collectNonEmpty(session.offerB);

            if (!hasEnoughSpace(pA, toA) || !hasEnoughSpace(pB, toB)) {
                cancelTrade(session, "trade.partner-no-space");
                return;
            }

            long netDeltaA = session.creditsB - session.creditsA;
            long netDeltaB = session.creditsA - session.creditsB;

            if (netDeltaA != 0 || netDeltaB != 0) {
                db.executeInTransaction(conn -> {
                    profA.setCredits(profA.getCredits() + netDeltaA);
                    profB.setCredits(profB.getCredits() + netDeltaB);
                    db.savePlayerInternal(conn, profA);
                    db.savePlayerInternal(conn, profB);
                    db.insertTransactionInternal(conn, session.playerA, TransactionType.TRADE,
                            netDeltaA, 0, "Trade with " + pB.getName());
                    db.insertTransactionInternal(conn, session.playerB, TransactionType.TRADE,
                            netDeltaB, 0, "Trade with " + pA.getName());
                    return null;
                });
            }

            session.finished = true;
            activeSessions.remove(session.playerA);
            activeSessions.remove(session.playerB);

            for (int i = 0; i < session.offerA.length; i++) {
                session.offerA[i] = null;
                session.offerB[i] = null;
            }

            for (ItemStack item : toA) {
                pA.getInventory().addItem(item);
            }
            for (ItemStack item : toB) {
                pB.getInventory().addItem(item);
            }

            pA.closeInventory();
            pB.closeInventory();
            pA.playSound(pA.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
            pB.playSound(pB.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
            pA.sendMessage(configManager.msg("trade.completed", Map.of("partner", pB.getName())));
            pB.sendMessage(configManager.msg("trade.completed", Map.of("partner", pA.getName())));
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL failure during trade completion: " + e.getMessage());
            cancelTrade(session, "trade.cancelled");
        } finally {
            lock2.unlock();
            lock1.unlock();
        }
    }

    private void returnItems(Player player, ItemStack[] items) {
        if (player == null) {
            return;
        }
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item != null && !item.getType().isAir()) {
                var leftover = player.getInventory().addItem(item);
                leftover.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
                items[i] = null;
            }
        }
    }

    private List<ItemStack> collectNonEmpty(ItemStack[] items) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                list.add(item.clone());
            }
        }
        return list;
    }

    private boolean hasEnoughSpace(Player player, List<ItemStack> incoming) {
        int emptySlots = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                emptySlots++;
            }
        }
        return emptySlots >= incoming.size();
    }
}
