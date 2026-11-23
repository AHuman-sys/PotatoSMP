package com.potatosmp.economy;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.config.ConfigManager;
import com.potatosmp.util.CoreLogic;
import com.potatosmp.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class MarketService {

    private final PotatoSMPPlugin plugin;
    private final ConfigManager configManager;
    private volatile double multiplier = 1.0;
    private volatile String lastTrend = "<gray>Stable</gray>";
    private BukkitTask task;

    public MarketService(PotatoSMPPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void start() {
        stop();
        if (!configManager.isFeatureEnabled("market-fluctuation")) {
            this.multiplier = 1.0;
            this.lastTrend = "<gray>Fixed</gray>";
            return;
        }
        long minutes = Math.max(1L, configManager.getConfig().getLong("economy.market.update-interval-minutes", 30L));
        long ticks = minutes * 60L * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::fluctuate, ticks, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void fluctuate() {
        if (!configManager.isFeatureEnabled("market-fluctuation")) {
            multiplier = 1.0;
            lastTrend = "<gray>Fixed</gray>";
            return;
        }
        double min = configManager.getConfig().getDouble("economy.market.min-multiplier", 0.85);
        double max = configManager.getConfig().getDouble("economy.market.max-multiplier", 1.20);
        double step = configManager.getConfig().getDouble("economy.market.max-step-change", 0.08);
        double unit = (ThreadLocalRandom.current().nextDouble() * 2.0) - 1.0;

        double previous = multiplier;
        double next = CoreLogic.nextMarketMultiplier(previous, unit, step, min, max);
        multiplier = next;
        if (next > previous + 0.005) {
            lastTrend = "<green>▲ Rising</green>";
        } else if (next < previous - 0.005) {
            lastTrend = "<red>▼ Falling</red>";
        } else {
            lastTrend = "<yellow>◆ Steady</yellow>";
        }

        Bukkit.broadcast(configManager.msg("economy.market-shift", Map.of(
                "rate", TextUtil.formatRate(getRegularPotatoRate()),
                "trend", lastTrend
        )));
    }

    public double getMultiplier() {
        return multiplier;
    }

    public String getLastTrend() {
        return lastTrend;
    }

    public double getRegularPotatoRate() {
        double base = configManager.getConfig().getDouble("economy.potato-base-rate", 2.0);
        return Math.max(0.01, base * multiplier);
    }

    public double getGoldenPotatoRate() {
        double base = configManager.getConfig().getDouble("economy.golden-potato-rate", 120.0);
        return Math.max(1.0, base * multiplier);
    }

    public double getPoisonousPotatoRate() {
        return Math.max(0.0, configManager.getConfig().getDouble("economy.poisonous-potato-rate", 0.5));
    }
}
