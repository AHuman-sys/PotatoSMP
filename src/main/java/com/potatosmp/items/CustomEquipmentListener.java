package com.potatosmp.items;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.config.ConfigManager;
import com.potatosmp.database.PlayerProfile;
import com.potatosmp.economy.EconomyService;
import com.potatosmp.util.CoreLogic;
import com.potatosmp.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

public final class CustomEquipmentListener implements Listener {

    private final PotatoSMPPlugin plugin;
    private final ConfigManager configManager;
    private final EconomyService economyService;
    private final NamespacedKey heartBonusKey;
    private final Map<UUID, Boolean> spudSeekerActive = new ConcurrentHashMap<>();
    private BukkitTask passiveCheckTask;

    public CustomEquipmentListener(PotatoSMPPlugin plugin, ConfigManager configManager, EconomyService economyService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.economyService = economyService;
        this.heartBonusKey = new NamespacedKey(plugin, "potato_heart_bonus");
    }

    public void start() {
        stop();
        configureWorldLocatorBars();
        passiveCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                refreshPassiveEffects(player);
                syncSpudSeeker(player, false);
            }
        }, 20L, 40L);
    }

    public void stop() {
        if (passiveCheckTask != null) {
            passiveCheckTask.cancel();
            passiveCheckTask = null;
        }
        spudSeekerActive.clear();
    }

    public void configureWorldLocatorBars() {
        if (!configManager.isFeatureEnabled("locator-bar-spud-seeker")) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            try {
                world.setGameRule(GameRule.LOCATOR_BAR, true);
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not set LOCATOR_BAR gamerule on world " + world.getName() + ": " + t.getMessage());
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        economyService.getOrLoadProfile(player.getUniqueId(), player.getName());
        applyPotatoHeartAttribute(player);
        syncSpudSeeker(player, false);
        refreshPassiveEffects(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.getTradeService().onPlayerQuit(uuid);
        spudSeekerActive.remove(uuid);
        economyService.unloadProfile(uuid);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            applyPotatoHeartAttribute(player);
            syncSpudSeeker(player, false);
            refreshPassiveEffects(player);
        }, 2L);
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            refreshPassiveEffects(player);
            syncSpudSeeker(player, true);
        });
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> refreshPassiveEffects(player));
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> refreshPassiveEffects(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                refreshPassiveEffects(player);
                syncSpudSeeker(player, true);
            });
        }
    }

    // -------------------------------------------------------------------------
    // Potato Armor Full Set & Potato Sword Speed
    // -------------------------------------------------------------------------

    private void refreshPassiveEffects(Player player) {
        if (!player.isOnline()) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        boolean fullPotatoArmor = ItemFactory.getItemType(inv.getHelmet()) == PotatoItemType.POTATO_HELMET
                && ItemFactory.getItemType(inv.getChestplate()) == PotatoItemType.POTATO_CHESTPLATE
                && ItemFactory.getItemType(inv.getLeggings()) == PotatoItemType.POTATO_LEGGINGS
                && ItemFactory.getItemType(inv.getBoots()) == PotatoItemType.POTATO_BOOTS;

        boolean holdingPotatoSword = ItemFactory.getItemType(inv.getItemInMainHand()) == PotatoItemType.POTATO_SWORD
                || ItemFactory.getItemType(inv.getItemInOffHand()) == PotatoItemType.POTATO_SWORD;

        if (fullPotatoArmor || holdingPotatoSword) {
            int armorAmp = configManager.getConfig().getInt("items.armor.full-set-speed-amplifier", 0);
            int swordAmp = configManager.getConfig().getInt("items.sword.held-speed-amplifier", 0);
            int amplifier = Math.max(fullPotatoArmor ? armorAmp : 0, holdingPotatoSword ? swordAmp : 0);
            if (fullPotatoArmor && holdingPotatoSword) {
                amplifier = Math.min(2, amplifier + 1);
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 70, amplifier, true, false, true));
        }
    }

    // -------------------------------------------------------------------------
    // Bounty Hat: The Spud Seeker (Native 1.21.8 Locator Bar)
    // -------------------------------------------------------------------------

    public void syncSpudSeeker(Player player, boolean notifyOnChange) {
        if (!configManager.isFeatureEnabled("locator-bar-spud-seeker") || !player.isOnline()) {
            return;
        }
        AttributeInstance receiveAttr = player.getAttribute(Attribute.WAYPOINT_RECEIVE_RANGE);
        if (receiveAttr == null) {
            return;
        }

        boolean wearing = ItemFactory.getItemType(player.getInventory().getHelmet()) == PotatoItemType.SPUD_SEEKER;
        double revealRadius = configManager.getConfig().getDouble("items.spud-seeker.reveal-radius-blocks", 50.0);
        double baseline = configManager.getConfig().getDouble("items.spud-seeker.baseline-receive-range", 0.0);

        double targetRange = wearing ? revealRadius : baseline;
        if (Math.abs(receiveAttr.getBaseValue() - targetRange) > 0.01) {
            receiveAttr.setBaseValue(targetRange);
        }

        Boolean previous = spudSeekerActive.put(player.getUniqueId(), wearing);
        if (notifyOnChange && previous != null && previous != wearing) {
            if (wearing) {
                player.sendActionBar(configManager.rawMsg("items.spud-seeker-equipped",
                        Map.of("radius", String.valueOf((int) revealRadius))));
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.4f);
            } else {
                player.sendActionBar(configManager.rawMsg("items.spud-seeker-unequipped", Map.of()));
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.2f);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Potato Axe: Extended Shield Disable
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAxeHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player defender)) {
            return;
        }
        if (ItemFactory.getItemType(attacker.getInventory().getItemInMainHand()) != PotatoItemType.POTATO_AXE) {
            return;
        }
        if (defender.isBlocking()) {
            int disableSeconds = Math.max(5, configManager.getConfig().getInt("items.axe.shield-disable-seconds", 9));
            int ticks = disableSeconds * 20;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (defender.isOnline()) {
                    defender.setCooldown(Material.SHIELD, ticks);
                    defender.getWorld().playSound(defender.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0f, 0.85f);
                    attacker.sendActionBar(TextUtil.mm("<gold>Shield Shattered!</gold> <red>Disabled for " + disableSeconds + "s</red>"));
                }
            });
        }
    }

    // -------------------------------------------------------------------------
    // Potato Pickaxe: Auto-Smelt + Fortune
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickaxeDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (ItemFactory.getItemType(tool) != PotatoItemType.POTATO_PICKAXE) {
            return;
        }
        if (!configManager.getConfig().getBoolean("items.pickaxe.auto-smelt", true)) {
            return;
        }
        boolean smeltStone = configManager.getConfig().getBoolean("items.pickaxe.smelt-stone-to-smooth", false);
        boolean smeltedAny = false;
        int xpToAward = 0;

        for (Item droppedEntity : event.getItems()) {
            ItemStack dropStack = droppedEntity.getItemStack();
            var smelted = CoreLogic.getSmeltedResult(dropStack.getType(), smeltStone);
            if (smelted.isPresent()) {
                ItemStack converted = new ItemStack(smelted.get(), dropStack.getAmount());
                droppedEntity.setItemStack(converted);
                smeltedAny = true;
                xpToAward += Math.max(1, dropStack.getAmount() / 2);
            }
        }

        if (smeltedAny) {
            player.giveExp(xpToAward);
            Block block = event.getBlock();
            block.getWorld().spawnParticle(Particle.FLAME, block.getLocation().add(0.5, 0.5, 0.5), 8, 0.25, 0.25, 0.25, 0.02);
            player.playSound(block.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.35f, 1.6f);
        }
    }

    // -------------------------------------------------------------------------
    // Potato Hoe (Bonus Drops + Auto-Replant) & Golden Potato Drops
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCropBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.POTATOES) {
            return;
        }
        if (!(block.getBlockData() instanceof Ageable ageable)) {
            return;
        }
        if (ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean usingPotatoHoe = ItemFactory.getItemType(tool) == PotatoItemType.POTATO_HOE;

        // Golden Potato rare drop
        if (configManager.isFeatureEnabled("golden-potatoes")) {
            double baseChance = configManager.getConfig().getDouble("golden-potatoes.drop-chance", 0.015);
            double hoeMult = usingPotatoHoe
                    ? configManager.getConfig().getDouble("items.hoe.golden-potato-bonus-multiplier", 2.0)
                    : 1.0;
            if (ThreadLocalRandom.current().nextDouble() < (baseChance * hoeMult)) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.3, 0.5),
                        ItemFactory.create(PotatoItemType.GOLDEN_POTATO));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
                player.sendActionBar(TextUtil.mm("<gold><bold>✦ Rare Harvest!</bold></gold> <yellow>You found a Golden Potato!</yellow>"));
            }
        }

        if (!usingPotatoHoe) {
            return;
        }

        // Bonus potatoes
        int minB = configManager.getConfig().getInt("items.hoe.bonus-potatoes-min", 1);
        int maxB = configManager.getConfig().getInt("items.hoe.bonus-potatoes-max", 3);
        double chance = configManager.getConfig().getDouble("items.hoe.bonus-drop-chance", 0.75);
        int bonus = CoreLogic.calculateHoeBonusPotatoes(minB, maxB,
                ThreadLocalRandom.current().nextDouble(), chance, ThreadLocalRandom.current().nextDouble());
        if (bonus > 0) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.3, 0.5),
                    new ItemStack(Material.POTATO, bonus));
        }

        // Auto-replant safely (1 tick after block break completes, consuming 1 potato from nearby drop or player inventory)
        if (configManager.getConfig().getBoolean("items.hoe.auto-replant", true)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (block.getType().isAir() && block.getRelative(0, -1, 0).getType() == Material.FARMLAND) {
                    if (consumeOneDroppedPotato(block) || consumeOnePlainPotato(player)) {
                        block.setType(Material.POTATOES);
                        if (block.getBlockData() instanceof Ageable replanted) {
                            replanted.setAge(0);
                            block.setBlockData(replanted);
                        }
                        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                                block.getLocation().add(0.5, 0.4, 0.5), 4, 0.2, 0.1, 0.2, 0.01);
                    }
                }
            });
        }
    }

    private boolean consumeOneDroppedPotato(Block block) {
        for (var entity : block.getWorld().getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), 1.5, 1.5, 1.5)) {
            if (entity instanceof Item dropped) {
                ItemStack stack = dropped.getItemStack();
                if (stack.getType() == Material.POTATO && !ItemFactory.isPotatoItem(stack) && stack.getAmount() > 0) {
                    if (stack.getAmount() > 1) {
                        stack.setAmount(stack.getAmount() - 1);
                        dropped.setItemStack(stack);
                    } else {
                        dropped.remove();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private boolean consumeOnePlainPotato(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.POTATO && !ItemFactory.isPotatoItem(item)) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    contents[i] = null;
                }
                player.getInventory().setStorageContents(contents);
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Potato Heart Consumption & Persistent Max Health
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onHeartInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (ItemFactory.getItemType(item) != PotatoItemType.POTATO_HEART) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        int maxUpgrades = configManager.getConfig().getInt("items.potato-heart.max-upgrades", 8);
        UUID uuid = player.getUniqueId();
        ReentrantLock lock = economyService.lockFor(uuid);
        lock.lock();
        try {
            PlayerProfile profile = economyService.getOrLoadProfile(uuid, player.getName());
            if (!CoreLogic.canConsumePotatoHeart(profile.getPotatoHeartsUsed(), maxUpgrades)) {
                player.sendMessage(configManager.msg("items.heart-maxed", Map.of("max", String.valueOf(maxUpgrades))));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                return;
            }

            // Re-verify exact hand item before consuming
            EquipmentSlot hand = event.getHand();
            ItemStack liveHand = hand == EquipmentSlot.OFF_HAND
                    ? player.getInventory().getItemInOffHand()
                    : player.getInventory().getItemInMainHand();
            if (ItemFactory.getItemType(liveHand) != PotatoItemType.POTATO_HEART) {
                return;
            }

            if (liveHand.getAmount() > 1) {
                liveHand.setAmount(liveHand.getAmount() - 1);
            } else if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }

            int nextUsed = profile.getPotatoHeartsUsed() + 1;
            profile.setPotatoHeartsUsed(nextUsed);
            plugin.getDatabaseManager().savePlayer(profile);

            applyPotatoHeartAttribute(player);
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1.2, 0), 12, 0.4, 0.4, 0.4, 0.05);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.35f);
            player.sendMessage(configManager.msg("items.heart-used", Map.of(
                    "used", String.valueOf(nextUsed),
                    "max", String.valueOf(maxUpgrades)
            )));
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to persist Potato Heart consumption for " + uuid + ": " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public void applyPotatoHeartAttribute(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        maxHealth.removeModifier(heartBonusKey);
        PlayerProfile profile = economyService.getOrLoadProfile(player.getUniqueId(), player.getName());
        int maxCap = configManager.getConfig().getInt("items.potato-heart.max-upgrades", 8);
        int clampedUsed = Math.min(profile.getPotatoHeartsUsed(), maxCap);
        double healthPerUse = configManager.getConfig().getDouble("items.potato-heart.health-per-use", 1.0);
        double totalBonus = clampedUsed * healthPerUse;
        if (totalBonus > 0.0) {
            maxHealth.addModifier(new AttributeModifier(heartBonusKey, totalBonus, AttributeModifier.Operation.ADD_NUMBER));
        }
    }
}
