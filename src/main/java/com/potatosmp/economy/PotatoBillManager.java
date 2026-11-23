package com.potatosmp.economy;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.config.ConfigManager;
import com.potatosmp.gui.BankMenu;
import com.potatosmp.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

public final class PotatoBillManager implements Listener {

    private final PotatoSMPPlugin plugin;
    private final ConfigManager configManager;
    private final NamespacedKey npcKey;

    public PotatoBillManager(PotatoSMPPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.npcKey = new NamespacedKey(plugin, "potato_bill_npc");
    }

    public boolean isPotatoBill(Entity entity) {
        if (entity == null) {
            return false;
        }
        return entity.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE);
    }

    public void spawnOrRelocatePotatoBill(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        // Remove any previously tagged Potato Bill instances in loaded worlds
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isPotatoBill(entity)) {
                    entity.remove();
                }
            }
        }

        loc.getChunk().load();
        Villager villager = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        configureVillager(villager);

        configManager.setLocation("bank.npc", loc);
        configManager.getConfig().set("bank.npc.spawned", true);
        configManager.saveMainConfig();
    }

    public void ensureSpawnedIfConfigured() {
        if (!configManager.getConfig().getBoolean("bank.npc.spawned", false)) {
            return;
        }
        Location loc = configManager.getLocation("bank.npc");
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        Chunk chunk = loc.getChunk();
        if (!chunk.isLoaded()) {
            return;
        }
        for (Entity entity : chunk.getEntities()) {
            if (isPotatoBill(entity)) {
                if (entity instanceof Villager v) {
                    configureVillager(v);
                }
                return;
            }
        }
        Villager villager = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        configureVillager(villager);
    }

    private void configureVillager(Villager villager) {
        String name = configManager.getConfig().getString("bank.npc.name",
                "<gold><bold>Potato Bill</bold></gold> <yellow>[Banker]</yellow>");
        villager.customName(TextUtil.mm(name));
        villager.setCustomNameVisible(true);
        villager.setProfession(Villager.Profession.FARMER);
        villager.setVillagerType(Villager.Type.PLAINS);
        villager.setVillagerLevel(5);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setCollidable(false);
        villager.setPersistent(true);
        villager.setRemoveWhenFarAway(false);
        villager.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteractNpc(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!isPotatoBill(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("potatosmp.bank")) {
            player.sendMessage(configManager.msg("general.no-permission"));
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.9f, 1.15f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                event.getRightClicked().getLocation().add(0, 1.4, 0), 8, 0.35, 0.35, 0.35, 0.05);
        new BankMenu(plugin, player).open();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcDamage(EntityDamageEvent event) {
        if (isPotatoBill(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcTransform(EntityTransformEvent event) {
        if (isPotatoBill(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcTarget(EntityTargetEvent event) {
        if (isPotatoBill(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!configManager.getConfig().getBoolean("bank.npc.spawned", false)) {
            return;
        }
        Location loc = configManager.getLocation("bank.npc");
        if (loc == null || !loc.getWorld().equals(event.getWorld())) {
            return;
        }
        if ((loc.getBlockX() >> 4) == event.getChunk().getX() && (loc.getBlockZ() >> 4) == event.getChunk().getZ()) {
            for (Entity entity : event.getChunk().getEntities()) {
                if (isPotatoBill(entity) && entity instanceof Villager v) {
                    configureVillager(v);
                    return;
                }
            }
            Villager villager = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
            configureVillager(villager);
        }
    }
}
