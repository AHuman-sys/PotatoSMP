package com.potatosmp.items;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.block.Crafter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class ItemIntegrityListener implements Listener {

    private final PotatoSMPPlugin plugin;
    private final ConfigManager configManager;

    public ItemIntegrityListener(PotatoSMPPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * Prevents players from renaming/repairing custom PotatoSMP items in an anvil in a way
     * that strips their PDC identity, custom name theme, lore, or attribute modifiers.
     * Repairing with vanilla materials or combining enchanted books is still safe because
     * we re-assert the canonical ItemFactory metadata onto the anvil output!
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack first = inv.getFirstItem();
        ItemStack second = inv.getSecondItem();

        PotatoItemType firstType = ItemFactory.getItemType(first);
        PotatoItemType secondType = ItemFactory.getItemType(second);

        if (firstType == null && secondType == null) {
            return;
        }

        // Prevent combining unique consumable tokens (Potato Heart) or Golden Potatoes in anvils
        if ((firstType != null && (firstType.isUniqueToken() || firstType == PotatoItemType.GOLDEN_POTATO))
                || (secondType != null && (secondType.isUniqueToken() || secondType == PotatoItemType.GOLDEN_POTATO))) {
            event.setResult(null);
            return;
        }

        // Prevent merging two different PotatoSMP item types
        if (firstType != null && secondType != null && firstType != secondType) {
            event.setResult(null);
            return;
        }

        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir() || firstType == null) {
            return;
        }

        // Re-assert canonical PotatoSMP display name, lore, attributes, and PDC identity
        // while keeping valid enchantments & repaired durability.
        ItemStack sanitized = ItemFactory.create(firstType, result.getAmount());
        ItemMeta sanitizedMeta = sanitized.getItemMeta();
        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta != null) {
            for (var ench : resultMeta.getEnchants().entrySet()) {
                sanitizedMeta.addEnchant(ench.getKey(), ench.getValue(), true);
            }
            if (resultMeta instanceof org.bukkit.inventory.meta.Damageable resDmg
                    && sanitizedMeta instanceof org.bukkit.inventory.meta.Damageable sanDmg) {
                sanDmg.setDamage(resDmg.getDamage());
            }
        }
        sanitized.setItemMeta(sanitizedMeta);
        event.setResult(sanitized);
    }

    /**
     * Blocks grindstone disenchanting if it would strip custom PotatoSMP item attributes or glint,
     * or preserves canonical PotatoSMP traits cleanly.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        ItemStack upper = event.getInventory().getUpperItem();
        ItemStack lower = event.getInventory().getLowerItem();
        if (ItemFactory.isPotatoItem(upper) || ItemFactory.isPotatoItem(lower)) {
            if (ItemFactory.getItemType(upper) == PotatoItemType.POTATO_PICKAXE
                    || ItemFactory.getItemType(lower) == PotatoItemType.POTATO_PICKAXE
                    || ItemFactory.getItemType(upper) == PotatoItemType.POTATO_HEART
                    || ItemFactory.getItemType(lower) == PotatoItemType.POTATO_HEART) {
                event.setResult(null);
                return;
            }
            ItemStack result = event.getResult();
            if (result != null && !result.getType().isAir()) {
                PotatoItemType type = ItemFactory.getItemType(upper) != null
                        ? ItemFactory.getItemType(upper)
                        : ItemFactory.getItemType(lower);
                if (type != null) {
                    ItemStack refreshed = ItemFactory.create(type, result.getAmount());
                    event.setResult(refreshed);
                }
            }
        }
    }

    /**
     * Prevents using PotatoSMP custom equipment in vanilla repair-crafting grids
     * (which would output a plain vanilla un-tagged item) and enforces exact Golden Potato recipes.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack[] matrix = event.getInventory().getMatrix();
        boolean hasCustomEquip = false;
        boolean hasGoldenPotato = false;
        boolean hasVanillaPotato = false;

        for (ItemStack item : matrix) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            PotatoItemType type = ItemFactory.getItemType(item);
            if (type == PotatoItemType.GOLDEN_POTATO) {
                hasGoldenPotato = true;
            } else if (type != null) {
                hasCustomEquip = true;
            } else if (item.getType() == Material.POTATO) {
                hasVanillaPotato = true;
            }
        }

        if (hasCustomEquip) {
            event.getInventory().setResult(null);
            return;
        }

        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }

        PotatoItemType resultType = ItemFactory.getItemType(result);
        if (resultType == PotatoItemType.GOLDEN_BAKED_POTATO && (!hasGoldenPotato || hasVanillaPotato)) {
            // Custom Golden Baked Potato recipe strictly requires genuine Golden Potatoes
            event.getInventory().setResult(null);
        } else if (resultType == null && hasGoldenPotato) {
            // Prevent accidentally burning a rare Golden Potato in a non-Golden recipe
            event.getInventory().setResult(null);
        }
    }

    /**
     * Prevents PotatoSMP armor/tools from being altered in a Smithing Table.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        for (ItemStack item : event.getInventory().getContents()) {
            if (ItemFactory.isPotatoItem(item)) {
                event.setResult(null);
                return;
            }
        }
    }

    /**
     * Prevents 1.21 Crafter blocks from consuming PotatoSMP custom items in generic recipes.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        if (event.getBlock().getState() instanceof Crafter crafter) {
            for (ItemStack item : crafter.getInventory().getContents()) {
                if (ItemFactory.isPotatoItem(item) && ItemFactory.getItemType(item) != PotatoItemType.GOLDEN_POTATO) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    /**
     * Handles consuming Golden Baked Potato buff food and prevents eating raw Golden Potatoes directly.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        PotatoItemType type = ItemFactory.getItemType(event.getItem());
        if (type == null) {
            return;
        }
        Player player = event.getPlayer();
        if (type == PotatoItemType.GOLDEN_POTATO || type == PotatoItemType.POTATO_HEART) {
            event.setCancelled(true);
            return;
        }
        if (type == PotatoItemType.GOLDEN_BAKED_POTATO) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 1, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 1200, 0, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 40, 0, false, false));
        }
    }
}
