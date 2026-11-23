package com.potatosmp.config;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public final class ConfigManager {

    private final PotatoSMPPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration shopConfig;
    private FileConfiguration messagesConfig;

    private File shopFile;
    private File messagesFile;

    public ConfigManager(PotatoSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        this.shopFile = new File(plugin.getDataFolder(), "shop.yml");
        if (!shopFile.exists()) {
            plugin.saveResource("shop.yml", false);
        }
        this.shopConfig = YamlConfiguration.loadConfiguration(shopFile);

        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getShopConfig() {
        return shopConfig;
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public void saveMainConfig() {
        plugin.saveConfig();
    }

    public void saveShopConfig() {
        try {
            shopConfig.save(shopFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save shop.yml: " + e.getMessage());
        }
    }

    // Feature toggles
    public boolean isFeatureEnabled(String key) {
        return config.getBoolean("features." + key, true);
    }

    // Messages helpers
    public Component msg(String path) {
        return msg(path, Map.of());
    }

    public Component msg(String path, Map<String, String> placeholders) {
        String prefix = messagesConfig.getString("prefix", "<gold>[PotatoSMP]</gold> ");
        String body = messagesConfig.getString(path, "<red>Missing message: " + path + "</red>");
        return TextUtil.mm(prefix + body, placeholders);
    }

    public Component rawMsg(String path, Map<String, String> placeholders) {
        String body = messagesConfig.getString(path, "<red>Missing message: " + path + "</red>");
        return TextUtil.mm(body, placeholders);
    }

    // GUI Materials
    public Material getFillerMaterial() {
        Material m = Material.matchMaterial(config.getString("gui.filler-material", "BROWN_STAINED_GLASS_PANE"));
        return m != null ? m : Material.BROWN_STAINED_GLASS_PANE;
    }

    public Material getAccentMaterial() {
        Material m = Material.matchMaterial(config.getString("gui.accent-material", "YELLOW_STAINED_GLASS_PANE"));
        return m != null ? m : Material.YELLOW_STAINED_GLASS_PANE;
    }

    // Location serialization helpers
    public void setLocation(String basePath, Location loc) {
        config.set(basePath + ".world", loc.getWorld().getName());
        config.set(basePath + ".x", loc.getX());
        config.set(basePath + ".y", loc.getY());
        config.set(basePath + ".z", loc.getZ());
        config.set(basePath + ".yaw", loc.getYaw());
        config.set(basePath + ".pitch", loc.getPitch());
        saveMainConfig();
    }

    public Location getLocation(String basePath) {
        String worldName = config.getString(basePath + ".world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().getFirst();
        }
        if (world == null) {
            return null;
        }
        double x = config.getDouble(basePath + ".x", 0.5);
        double y = config.getDouble(basePath + ".y", 64.0);
        double z = config.getDouble(basePath + ".z", 0.5);
        float yaw = (float) config.getDouble(basePath + ".yaw", 0.0);
        float pitch = (float) config.getDouble(basePath + ".pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }
}
