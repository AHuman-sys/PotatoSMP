package com.potatosmp.features;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.config.ConfigManager;
import com.potatosmp.database.BountyRecord;
import com.potatosmp.database.PlayerProfile;
import com.potatosmp.economy.EconomyService;
import com.potatosmp.economy.MarketService;
import com.potatosmp.util.TextUtil;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Optional;

public final class ScoreboardService {

    private final PotatoSMPPlugin plugin;
    private final ConfigManager configManager;
    private final EconomyService economy;
    private final MarketService market;
    private final CompetitionService competition;
    private final MonumentService monument;
    private BukkitTask refreshTask;

    public ScoreboardService(PotatoSMPPlugin plugin, ConfigManager configManager, EconomyService economy,
                             MarketService market, CompetitionService competition, MonumentService monument) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.economy = economy;
        this.market = market;
        this.competition = competition;
        this.monument = monument;
    }

    public void start() {
        stop();
        if (!configManager.isFeatureEnabled("scoreboard")) {
            return;
        }
        long seconds = Math.max(2L, configManager.getConfig().getLong("gui.scoreboard-refresh-seconds", 5L));
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updatePlayer(player);
            }
        }, 20L, seconds * 20L);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void updatePlayer(Player player) {
        if (!configManager.isFeatureEnabled("scoreboard")) {
            return;
        }
        PlayerProfile profile = economy.getOrLoadProfile(player.getUniqueId(), player.getName());
        if (!profile.isScoreboardEnabled()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            return;
        }

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("potatosmp", Criteria.DUMMY,
                TextUtil.mm("<gold><bold>✦ POTATO SMP ✦</bold></gold>"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.numberFormat(NumberFormat.blank());

        Optional<BountyRecord> bounty = plugin.getBountyService().getActiveBounty(player.getUniqueId());
        int weeklyRank = competition.getPlayerRank(player.getUniqueId());

        String[] lines = new String[]{
                "<dark_gray>───────────────</dark_gray>",
                "<gold>Credits:</gold> <yellow>" + TextUtil.formatCredits(profile.getCredits()) + "</yellow>",
                "<green>Potatoes:</green> <yellow>" + TextUtil.formatCredits(profile.getLifetimePotatoes()) + "</yellow>",
                "<gold>Market:</gold> <yellow>" + TextUtil.formatRate(market.getRegularPotatoRate()) + "/spud</yellow>",
                "<dark_gray>─────────────── </dark_gray>",
                "<red>Your Bounty:</red> " + (bounty.isPresent()
                        ? "<gold>" + TextUtil.formatCredits(bounty.get().totalAmount()) + "</gold>"
                        : "<gray>None</gray>"),
                "<yellow>Weekly Rank:</yellow> " + (weeklyRank > 0 ? "<green>#" + weeklyRank + "</green>" : "<gray>Unranked</gray>"),
                "<gold>Monument:</gold> <green>" + TextUtil.formatCredits(monument.getTotalDeposited()) + "</green>",
                "<dark_gray>───────────────  </dark_gray>"
        };

        int score = lines.length;
        for (String line : lines) {
            String entryKey = "\u00A7" + Integer.toHexString(score);
            var team = board.registerNewTeam("line_" + score);
            team.addEntry(entryKey);
            team.prefix(TextUtil.mm(line));
            obj.getScore(entryKey).setScore(score);
            score--;
        }

        player.setScoreboard(board);
    }
}
