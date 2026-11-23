package com.potatosmp.netherite;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.config.ConfigManager;
import com.potatosmp.util.CoreLogic;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.SmithingTransformRecipe;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public final class NetheriteRestrictionListener implements Listener {

    public enum RestrictionMode {
        BLOCK_USE,
        CONFISCATE,
        CONVERT,
        RECIPES_ONLY
    }

    private final PotatoSMPPlugin plugin;
    private final ConfigManager configManager;
    private final Set<NamespacedKey> hiddenRecipeKeys = new HashSet<>();

    public NetheriteRestrictionListener(PotatoSMPPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void applyRecipeRestrictions() {
        hiddenRecipeKeys.clear();
        if (!configManager.isFeatureEnabled("netherite-ban")) {
            return;
        }
        boolean blockCraft = configManager.getConfig().getBoolean("netherite.block-crafting", true);
        boolean blockSmithing = configManager.getConfig().getBoolean("netherite.block-smithing-upgrades", true);
        boolean blockIngot = configManager.getConfig().getBoolean("netherite.block-netherite-ingot-craft", true);

        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            ItemStack result = recipe.getResult();
            Material mat = result.getType();
            boolean isNetheriteEquip = CoreLogic.isBannedNetheriteEquipment(mat);
            boolean isNetheriteMaterial = mat == Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE
                    || (blockIngot && (mat == Material.NETHERITE_INGOT || mat == Material.NETHERITE_BLOCK));

            boolean shouldRemove = false;
            if (recipe instanceof SmithingTransformRecipe && blockSmithing && isNetheriteEquip) {
                shouldRemove = true;
            } else if (blockCraft && (isNetheriteEquip || isNetheriteMaterial)) {
                shouldRemove = true;
            }

            if (shouldRemove) {
                if (recipe instanceof Keyed keyed) {
                    hiddenRecipeKeys.add(keyed.getKey());
                }
                it.remove();
            }
        }

        if (configManager.getConfig().getBoolean("netherite.hide-recipes-from-book", true) && !hiddenRecipeKeys.isEmpty()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.undiscoverRecipes(hiddenRecipeKeys);
            }
        }
    }

    public RestrictionMode getRestrictionMode() {
        String raw = configManager.getConfig().getString("netherite.restriction-mode", "BLOCK_USE");
        try {
            return RestrictionMode.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return RestrictionMode.BLOCK_USE;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!configManager.isFeatureEnabled("netherite-ban")) {
            return;
        }
        if (configManager.getConfig().getBoolean("netherite.hide-recipes-from-book", true) && !hiddenRecipeKeys.isEmpty()) {
            event.getPlayer().undiscoverRecipes(hiddenRecipeKeys);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!configManager.isFeatureEnabled("netherite-ban")
                || !configManager.getConfig().getBoolean("netherite.block-crafting", true)) {
            return;
        }
        ItemStack result = event.getInventory().getResult();
        if (result == null) {
            return;
        }
        Material mat = result.getType();
        boolean blockIngot = configManager.getConfig().getBoolean("netherite.block-netherite-ingot-craft", true);
        if (CoreLogic.isBannedNetheriteEquipment(mat)
                || mat == Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE
                || (blockIngot && (mat == Material.NETHERITE_INGOT || mat == Material.NETHERITE_BLOCK))) {
            event.getInventory().setResult(null);
            if (event.getView().getPlayer() instanceof Player player) {
                player.sendActionBar(configManager.rawMsg("netherite.craft-blocked", java.util.Map.of()));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (!configManager.isFeatureEnabled("netherite-ban")
                || !configManager.getConfig().getBoolean("netherite.block-smithing-upgrades", true)) {
            return;
        }
        ItemStack result = event.getResult();
        if (result != null && CoreLogic.isBannedNetheriteEquipment(result.getType())) {
            event.setResult(null);
            if (event.getView().getPlayer() instanceof Player player) {
                player.sendActionBar(configManager.rawMsg("netherite.craft-blocked", java.util.Map.of()));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || shouldBypass(player)) {
            return;
        }
        if (getRestrictionMode() == RestrictionMode.RECIPES_ONLY) {
            return;
        }
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            ItemStack cursor = event.getCursor();
            if (CoreLogic.isBannedNetheriteEquipment(cursor.getType())) {
                event.setCancelled(true);
                handleBannedItem(player, cursor);
            }
        } else if (event.isShiftClick() && event.getCurrentItem() != null
                && CoreLogic.isBannedNetheriteEquipment(event.getCurrentItem().getType())) {
            Material m = event.getCurrentItem().getType();
            if (m.name().endsWith("_HELMET") || m.name().endsWith("_CHESTPLATE")
                    || m.name().endsWith("_LEGGINGS") || m.name().endsWith("_BOOTS")) {
                event.setCancelled(true);
                handleBannedItem(player, event.getCurrentItem());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispenseArmor(BlockDispenseArmorEvent event) {
        if (!(event.getTargetEntity() instanceof Player player) || shouldBypass(player)) {
            return;
        }
        if (getRestrictionMode() == RestrictionMode.RECIPES_ONLY) {
            return;
        }
        if (CoreLogic.isBannedNetheriteEquipment(event.getItem().getType())) {
            event.setCancelled(true);
            player.sendMessage(configManager.msg("netherite.use-blocked"));
        }
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        if (shouldBypass(player) || getRestrictionMode() == RestrictionMode.RECIPES_ONLY) {
            return;
        }
        ItemStack newItem = event.getNewItem();
        if (!CoreLogic.isBannedNetheriteEquipment(newItem.getType())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            ItemStack equipped = switch (event.getSlotType()) {
                case HEAD -> player.getInventory().getHelmet();
                case CHEST -> player.getInventory().getChestplate();
                case LEGS -> player.getInventory().getLeggings();
                case FEET -> player.getInventory().getBoots();
            };
            if (equipped == null || !CoreLogic.isBannedNetheriteEquipment(equipped.getType())) {
                return;
            }
            RestrictionMode mode = getRestrictionMode();
            if (mode == RestrictionMode.CONFISCATE) {
                setArmorSlot(player, event.getSlotType(), null);
                player.sendMessage(configManager.msg("netherite.confiscated"));
            } else if (mode == RestrictionMode.CONVERT) {
                ItemStack converted = equipped.clone();
                converted.setType(CoreLogic.downgradeNetheriteToDiamond(equipped.getType()).orElse(equipped.getType()));
                setArmorSlot(player, event.getSlotType(), converted);
                player.sendMessage(configManager.msg("netherite.converted"));
            } else {
                setArmorSlot(player, event.getSlotType(), null);
                var leftover = player.getInventory().addItem(equipped);
                leftover.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
                player.sendMessage(configManager.msg("netherite.use-blocked"));
            }
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 0.7f);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (shouldBypass(player) || getRestrictionMode() == RestrictionMode.RECIPES_ONLY) {
            return;
        }
        ItemStack item = event.getItem();
        if (item != null && CoreLogic.isBannedNetheriteEquipment(item.getType())) {
            event.setCancelled(true);
            handleBannedItem(player, item);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (shouldBypass(player) || getRestrictionMode() == RestrictionMode.RECIPES_ONLY) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (CoreLogic.isBannedNetheriteEquipment(hand.getType())) {
            event.setCancelled(true);
            handleBannedItem(player, hand);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || shouldBypass(player)) {
            return;
        }
        if (getRestrictionMode() == RestrictionMode.RECIPES_ONLY) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (CoreLogic.isBannedNetheriteEquipment(hand.getType())) {
            event.setCancelled(true);
            handleBannedItem(player, hand);
        }
    }

    private void handleBannedItem(Player player, ItemStack stack) {
        RestrictionMode mode = getRestrictionMode();
        if (mode == RestrictionMode.CONFISCATE) {
            stack.setAmount(0);
            player.sendMessage(configManager.msg("netherite.confiscated"));
        } else if (mode == RestrictionMode.CONVERT) {
            stack.setType(CoreLogic.downgradeNetheriteToDiamond(stack.getType()).orElse(stack.getType()));
            player.sendMessage(configManager.msg("netherite.converted"));
        } else {
            player.sendMessage(configManager.msg("netherite.use-blocked"));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.9f);
    }

    private void setArmorSlot(Player player, PlayerArmorChangeEvent.SlotType slotType, ItemStack item) {
        switch (slotType) {
            case HEAD -> player.getInventory().setHelmet(item);
            case CHEST -> player.getInventory().setChestplate(item);
            case LEGS -> player.getInventory().setLeggings(item);
            case FEET -> player.getInventory().setBoots(item);
        }
    }

    private boolean shouldBypass(Player player) {
        return !configManager.isFeatureEnabled("netherite-ban")
                || player.hasPermission("potatosmp.bypass.netherite");
    }
}
