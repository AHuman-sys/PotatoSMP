package com.potatosmp.features;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.config.ConfigManager;
import com.potatosmp.database.DatabaseManager;
import com.potatosmp.database.TransactionType;
import com.potatosmp.economy.EconomyService;
import com.potatosmp.items.ItemFactory;
import com.potatosmp.items.PotatoItemType;
import com.potatosmp.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CompetitionService {

    private final PotatoSMPPlugin plugin;
    private final ConfigManager configManager;
    private final DatabaseManager db;
    private final EconomyService economy;
    private volatile DatabaseManager.WeeklyCompetition currentWeek;
    private BukkitTask checkTask;

    public CompetitionService(PotatoSMPPlugin plugin, ConfigManager configManager, DatabaseManager db, EconomyService economy) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.db = db;
        this.economy = economy;
    }

    public void start() {
        stop();
        if (!configManager.isFeatureEnabled("weekly-competition")) {
            return;
        }
        loadOrRolloverWeek();
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkRollover, 20L * 60L, 20L * 60L * 5L);
    }

    public void stop() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
    }

    public long getCurrentWeekId() {
        if (currentWeek == null) {
            loadOrRolloverWeek();
        }
        return currentWeek != null ? currentWeek.weekId() : -1L;
    }

    public DatabaseManager.WeeklyCompetition getCurrentWeek() {
        if (currentWeek == null) {
            loadOrRolloverWeek();
        }
        return currentWeek;
    }

    private void loadOrRolloverWeek() {
        long days = Math.max(1L, configManager.getConfig().getLong("weekly-competition.duration-days", 7L));
        long durationMillis = days * 86_400_000L;
        try {
            currentWeek = db.getOrCreateCurrentWeek(durationMillis);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load weekly competition: " + e.getMessage());
        }
    }

    public void checkRollover() {
        if (!configManager.isFeatureEnabled("weekly-competition")) {
            return;
        }
        if (currentWeek == null) {
            loadOrRolloverWeek();
            return;
        }
        if (System.currentTimeMillis() >= currentWeek.endsAt()) {
            finalizeAndStartNextWeek();
        }
    }

    public void finalizeAndStartNextWeek() {
        if (currentWeek == null) {
            return;
        }
        try {
            List<DatabaseManager.WeeklyEntry> winners = db.getWeeklyTopEntries(currentWeek.weekId(), 3);
            db.finalizeWeek(currentWeek.weekId());

            for (int i = 0; i < winners.size(); i++) {
                int rank = i + 1;
                DatabaseManager.WeeklyEntry entry = winners.get(i);
                long creditReward = configManager.getConfig().getLong("weekly-competition.rewards." + rank + ".credits", 0L);
                int gpReward = configManager.getConfig().getInt("weekly-competition.rewards." + rank + ".golden-potatoes", 0);

                if (creditReward > 0) {
                    economy.addCredits(entry.playerUuid(), entry.playerName(), creditReward,
                            TransactionType.COMPETITION_REWARD, "Weekly Potato Competition Rank #" + rank);
                }
                if (gpReward > 0) {
                    Player online = Bukkit.getPlayer(entry.playerUuid());
                    if (online != null && online.isOnline()) {
                        ItemStack gp = ItemFactory.create(PotatoItemType.GOLDEN_POTATO, gpReward);
                        var left = online.getInventory().addItem(gp);
                        left.values().forEach(d -> online.getWorld().dropItemNaturally(online.getLocation(), d));
                    }
                }
                if (rank == 1) {
                    Bukkit.broadcast(configManager.msg("competition.winner-broadcast", Map.of(
                            "player", entry.playerName(),
                            "potatoes", TextUtil.formatCredits(entry.potatoesDeposited()),
                            "credits", TextUtil.formatCredits(creditReward)
                    )));
                }
            }
            currentWeek = null;
            loadOrRolloverWeek();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed finalizing weekly competition: " + e.getMessage());
        }
    }

    public List<DatabaseManager.WeeklyEntry> getTopEntries(int limit) {
        long id = getCurrentWeekId();
        if (id <= 0) {
            return List.of();
        }
        try {
            return db.getWeeklyTopEntries(id, limit);
        } catch (SQLException e) {
            return List.of();
        }
    }

    public int getPlayerRank(UUID uuid) {
        long id = getCurrentWeekId();
        if (id <= 0) {
            return -1;
        }
        try {
            return db.getPlayerWeeklyRank(id, uuid);
        } catch (SQLException e) {
            return -1;
        }
    }
}
