package com.potatosmp.features;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.config.ConfigManager;
import com.potatosmp.database.DatabaseManager;
import com.potatosmp.database.TransactionType;
import com.potatosmp.economy.EconomyService;
import com.potatosmp.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

public final class MonumentService {

    private final PotatoSMPPlugin plugin;
    private final ConfigManager configManager;
    private final DatabaseManager db;
    private final EconomyService economy;
    private final NamespacedKey hologramKey;

    public MonumentService(PotatoSMPPlugin plugin, ConfigManager configManager, DatabaseManager db, EconomyService economy) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.db = db;
        this.economy = economy;
        this.hologramKey = new NamespacedKey(plugin, "monument_hologram");
    }

    public void setMonumentLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        // Place a simple polished gold/hay pedestal landmark block at base if air, and spawn hologram above it
        Location base = loc.getBlock().getLocation();
        if (base.getBlock().getType().isAir()) {
            base.getBlock().setType(Material.GOLD_BLOCK);
        }
        configManager.setLocation("monument.location", loc);
        configManager.getConfig().set("monument.location.configured", true);
        configManager.saveMainConfig();
        updateHologram();
    }

    public long getTotalDeposited() {
        try {
            return db.getMonumentTotal();
        } catch (SQLException e) {
            return 0L;
        }
    }

    public void onPotatoesDeposited(long updatedTotal) {
        if (!configManager.isFeatureEnabled("community-monument")) {
            return;
        }
        checkMilestones(updatedTotal);
        updateHologram();
    }

    private void checkMilestones(long total) {
        ConfigurationSection section = configManager.getConfig().getConfigurationSection("monument.milestones");
        if (section == null) {
            return;
        }
        Set<String> unlocked;
        try {
            unlocked = db.getUnlockedMilestones();
        } catch (SQLException e) {
            return;
        }

        for (String key : section.getKeys(false)) {
            if (unlocked.contains(key)) {
                continue;
            }
            long req = section.getLong(key + ".potatoes", Long.MAX_VALUE);
            if (total >= req) {
                String title = section.getString(key + ".title", key);
                long creditReward = section.getLong(key + ".reward-credits-online", 250L);
                int hasteMinutes = section.getInt(key + ".haste-duration-minutes", 15);
                try {
                    db.markMilestoneUnlocked(key);
                } catch (SQLException ignored) {}

                Bukkit.broadcast(configManager.msg("monument.milestone-unlocked", Map.of(
                        "title", title,
                        "potatoes", TextUtil.formatCredits(req),
                        "credits", TextUtil.formatCredits(creditReward)
                )));

                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (creditReward > 0) {
                        economy.addCredits(online.getUniqueId(), online.getName(), creditReward,
                                TransactionType.MONUMENT_REWARD, "Monument Milestone: " + title);
                    }
                    if (hasteMinutes > 0) {
                        online.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, hasteMinutes * 60 * 20, 0));
                    }
                }

                Location loc = configManager.getLocation("monument.location");
                if (loc != null && loc.getWorld() != null && configManager.getConfig().getBoolean("monument.location.configured", false)) {
                    Firework fw = (Firework) loc.getWorld().spawnEntity(loc.clone().add(0, 2.5, 0), EntityType.FIREWORK_ROCKET);
                    FireworkMeta meta = fw.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder()
                            .withColor(Color.YELLOW, Color.ORANGE)
                            .with(FireworkEffect.Type.STAR)
                            .build());
                    meta.setPower(1);
                    fw.setFireworkMeta(meta);
                }
            }
        }
    }

    public void updateHologram() {
        if (!configManager.isFeatureEnabled("community-monument")
                || !configManager.getConfig().getBoolean("monument.location.configured", false)) {
            return;
        }
        Location loc = configManager.getLocation("monument.location");
        if (loc == null || loc.getWorld() == null) {
            return;
        }

        // Clean existing monument holograms
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntitiesByClass(TextDisplay.class)) {
                if (e.getPersistentDataContainer().has(hologramKey, PersistentDataType.BYTE)) {
                    e.remove();
                }
            }
        }

        long total = getTotalDeposited();
        String nextTitle = "Max Tier Reached!";
        long nextTarget = total;
        ConfigurationSection section = configManager.getConfig().getConfigurationSection("monument.milestones");
        if (section != null) {
            var nextOpt = section.getKeys(false).stream()
                    .map(k -> Map.entry(section.getString(k + ".title", k), section.getLong(k + ".potatoes", Long.MAX_VALUE)))
                    .filter(e -> e.getValue() > total)
                    .min(Comparator.comparingLong(Map.Entry::getValue));
            if (nextOpt.isPresent()) {
                nextTitle = nextOpt.get().getKey();
                nextTarget = nextOpt.get().getValue();
            }
        }

        Location holoLoc = loc.clone().add(0, 2.1, 0);
        TextDisplay display = (TextDisplay) holoLoc.getWorld().spawnEntity(holoLoc, EntityType.TEXT_DISPLAY);
        display.getPersistentDataContainer().set(hologramKey, PersistentDataType.BYTE, (byte) 1);
        display.setBillboard(Display.Billboard.CENTER);
        display.setShadowed(true);
        display.setSeeThrough(false);
        display.setBackgroundColor(Color.fromARGB(160, 48, 32, 16));
        display.text(TextUtil.mm(
                "<gold><bold>✦ Community Potato Monument ✦</bold></gold>\n"
                        + "<yellow>Total Deposited:</yellow> <green><bold>" + TextUtil.formatCredits(total) + "</bold> Potatoes</green>\n"
                        + "<gray>Next Milestone:</gray> <gold>" + nextTitle + "</gold> <yellow>("
                        + TextUtil.formatCredits(nextTarget) + ")</yellow>"
        ));
    }
}
