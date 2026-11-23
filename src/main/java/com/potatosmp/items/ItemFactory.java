package com.potatosmp.items;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.config.ConfigManager;
import com.potatosmp.util.TextUtil;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public final class ItemFactory {

    private static NamespacedKey itemIdKey;
    private static NamespacedKey itemVersionKey;
    private static NamespacedKey uniqueTokenKey;
    private static PotatoSMPPlugin pluginRef;

    private static final int ITEM_SCHEMA_VERSION = 1;

    private ItemFactory() {}

    public static void init(PotatoSMPPlugin plugin) {
        pluginRef = plugin;
        itemIdKey = new NamespacedKey(plugin, "item_id");
        itemVersionKey = new NamespacedKey(plugin, "item_version");
        uniqueTokenKey = new NamespacedKey(plugin, "unique_token");
    }

    public static boolean isPotatoItem(ItemStack stack) {
        return getItemType(stack) != null;
    }

    public static PotatoItemType getItemType(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta() || itemIdKey == null) {
            return null;
        }
        String raw = stack.getItemMeta().getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
        return PotatoItemType.fromId(raw);
    }

    public static ItemStack create(PotatoItemType type) {
        return create(type, 1);
    }

    public static ItemStack create(PotatoItemType type, int amount) {
        ConfigManager cfg = pluginRef.getConfigManager();
        ItemStack stack = new ItemStack(type.getMaterial(), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(itemIdKey, PersistentDataType.STRING, type.getId());
        pdc.set(itemVersionKey, PersistentDataType.INTEGER, ITEM_SCHEMA_VERSION);
        if (type.isUniqueToken()) {
            pdc.set(uniqueTokenKey, PersistentDataType.STRING, UUID.randomUUID().toString());
            meta.setMaxStackSize(1);
        }

        meta.displayName(TextUtil.mm(type.getDefaultDisplayName()));

        switch (type) {
            case POTATO_HELMET -> configureArmorPiece(meta, EquipmentSlotGroup.HEAD, 3.0, 2.0, "helmet",
                    List.of(
                            "<gray>Forged from compressed tuber alloys.</gray>",
                            "<yellow>Protection:</yellow> <green>Diamond Equivalent</green>",
                            "<gold>Full Set Bonus:</gold> <yellow>Permanent Speed while worn</yellow>"
                    ), cfg);
            case POTATO_CHESTPLATE -> configureArmorPiece(meta, EquipmentSlotGroup.CHEST, 8.0, 2.0, "chestplate",
                    List.of(
                            "<gray>Reinforced golden-spud chestguard.</gray>",
                            "<yellow>Protection:</yellow> <green>Diamond Equivalent</green>",
                            "<gold>Full Set Bonus:</gold> <yellow>Permanent Speed while worn</yellow>"
                    ), cfg);
            case POTATO_LEGGINGS -> configureArmorPiece(meta, EquipmentSlotGroup.LEGS, 6.0, 2.0, "leggings",
                    List.of(
                            "<gray>Tailored for high-stakes SMP combat.</gray>",
                            "<yellow>Protection:</yellow> <green>Diamond Equivalent</green>",
                            "<gold>Full Set Bonus:</gold> <yellow>Permanent Speed while worn</yellow>"
                    ), cfg);
            case POTATO_BOOTS -> configureArmorPiece(meta, EquipmentSlotGroup.FEET, 3.0, 2.0, "boots",
                    List.of(
                            "<gray>Spring-heeled agricultural war boots.</gray>",
                            "<yellow>Protection:</yellow> <green>Diamond Equivalent</green>",
                            "<gold>Full Set Bonus:</gold> <yellow>Permanent Speed while worn</yellow>"
                    ), cfg);
            case POTATO_SWORD -> {
                double dmg = cfg.getConfig().getDouble("items.sword.attack-damage", 7.0);
                double spd = cfg.getConfig().getDouble("items.sword.attack-speed", -1.8);
                meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                        new NamespacedKey(pluginRef, "potato_sword_damage"),
                        dmg - 1.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                        new NamespacedKey(pluginRef, "potato_sword_speed"),
                        spd, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                meta.setEnchantmentGlintOverride(true);
                meta.lore(TextUtil.mmList(List.of(
                        "<gray>Lightweight blade built for relentless combos.</gray>",
                        "<yellow>Attack Speed:</yellow> <green>Fast Combo Swing</green>",
                        "<gold>Passive:</gold> <yellow>Grants Speed while held</yellow>"
                )));
            }
            case POTATO_AXE -> {
                double dmg = cfg.getConfig().getDouble("items.axe.attack-damage", 11.5);
                double spd = cfg.getConfig().getDouble("items.axe.attack-speed", -3.3);
                int shieldSec = cfg.getConfig().getInt("items.axe.shield-disable-seconds", 9);
                meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                        new NamespacedKey(pluginRef, "potato_axe_damage"),
                        dmg - 1.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                        new NamespacedKey(pluginRef, "potato_axe_speed"),
                        spd, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                meta.setEnchantmentGlintOverride(true);
                meta.lore(TextUtil.mmList(List.of(
                        "<gray>Massive cleaver that shatters defenses.</gray>",
                        "<yellow>Burst Damage:</yellow> <green>Heavy Strike</green>",
                        "<gold>Shield Breaker:</gold> <red>Disables shields for " + shieldSec + "s</red>"
                )));
            }
            case POTATO_PICKAXE -> {
                meta.setEnchantmentGlintOverride(true);
                meta.addEnchant(Enchantment.EFFICIENCY, 3, true);
                meta.lore(TextUtil.mmList(List.of(
                        "<gray>Superheated tuber-core mining pick.</gray>",
                        "<gold>Auto-Smelt:</gold> <yellow>Instantly smelts mined ores</yellow>",
                        "<green>Fortune Compatible:</green> <yellow>Multiplies smelted ingots</yellow>"
                )));
            }
            case POTATO_HOE -> {
                int minB = cfg.getConfig().getInt("items.hoe.bonus-potatoes-min", 1);
                int maxB = cfg.getConfig().getInt("items.hoe.bonus-potatoes-max", 3);
                meta.setEnchantmentGlintOverride(true);
                meta.lore(TextUtil.mmList(List.of(
                        "<gray>Blessed by the Grand Potato Monument.</gray>",
                        "<green>Bonus Harvest:</green> <yellow>+" + minB + " to +" + maxB + " extra potatoes</yellow>",
                        "<gold>Auto-Replant:</gold> <yellow>Replants mature potato crops</yellow>"
                )));
            }
            case POTATO_HEART -> {
                int maxHearts = cfg.getConfig().getInt("items.potato-heart.max-upgrades", 8);
                meta.setEnchantmentGlintOverride(true);
                meta.lore(TextUtil.mmList(List.of(
                        "<gray>A crystallized core of pure vitality.</gray>",
                        "<green>Right-Click:</green> <yellow>+0.5 Permanent Heart</yellow>",
                        "<gold>Limit:</gold> <yellow>Max " + maxHearts + " Potato Hearts per player</yellow>",
                        "<dark_gray>Soul-verified · Non-duplicable</dark_gray>"
                )));
            }
            case SPUD_SEEKER -> {
                double radius = cfg.getConfig().getDouble("items.spud-seeker.reveal-radius-blocks", 50.0);
                meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                        new NamespacedKey(pluginRef, "spud_seeker_armor"),
                        2.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
                meta.setEnchantmentGlintOverride(true);
                if (meta instanceof ArmorMeta armorMeta) {
                    armorMeta.setTrim(new ArmorTrim(TrimMaterial.GOLD, TrimPattern.EYE));
                }
                meta.addItemFlags(ItemFlag.HIDE_ARMOR_TRIM);
                meta.lore(TextUtil.mmList(List.of(
                        "<gray>Tuned to track nearby rivals across the SMP.</gray>",
                        "<gold>Locator Bar:</gold> <green>Reveals players within " + (int) radius + " blocks</green>",
                        "<yellow>Requirement:</yellow> <gray>Must be worn in the Helmet slot</gray>"
                )));
            }
            case GOLDEN_POTATO -> {
                meta.setEnchantmentGlintOverride(true);
                meta.lore(TextUtil.mmList(List.of(
                        "<gray>A rare shimmering harvest treasure.</gray>",
                        "<gold>Bank Value:</gold> <yellow>High-tier Credit payout at Potato Bill</yellow>",
                        "<green>Crafting:</green> <yellow>Used in Golden Potato recipes</yellow>"
                )));
            }
            case GOLDEN_BAKED_POTATO -> {
                meta.setEnchantmentGlintOverride(true);
                meta.lore(TextUtil.mmList(List.of(
                        "<gray>Infused with concentrated golden spud energy.</gray>",
                        "<green>Consume:</green> <yellow>Grants Regeneration II & Absorption</yellow>"
                )));
            }
        }

        stack.setItemMeta(meta);
        return stack;
    }

    private static void configureArmorPiece(ItemMeta meta, EquipmentSlotGroup slot,
                                            double armor, double toughness, String keySuffix,
                                            List<String> loreLines, ConfigManager cfg) {
        meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                new NamespacedKey(pluginRef, "potato_armor_" + keySuffix),
                armor, AttributeModifier.Operation.ADD_NUMBER, slot));
        meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(
                new NamespacedKey(pluginRef, "potato_toughness_" + keySuffix),
                toughness, AttributeModifier.Operation.ADD_NUMBER, slot));
        meta.setEnchantmentGlintOverride(true);

        if (meta instanceof LeatherArmorMeta leatherMeta) {
            String hex = cfg.getConfig().getString("items.armor.color-rgb", "#E6B83A").replace("#", "");
            try {
                leatherMeta.setColor(Color.fromRGB(Integer.parseInt(hex, 16)));
            } catch (NumberFormatException ignored) {
                leatherMeta.setColor(Color.fromRGB(0xE6B83A));
            }
            leatherMeta.addItemFlags(ItemFlag.HIDE_DYE);
        }
        if (meta instanceof ArmorMeta armorMeta) {
            armorMeta.setTrim(new ArmorTrim(TrimMaterial.GOLD, TrimPattern.TIDE));
            armorMeta.addItemFlags(ItemFlag.HIDE_ARMOR_TRIM);
        }
        meta.lore(TextUtil.mmList(loreLines));
    }

    /**
     * Re-asserts official PotatoSMP display name, lore, attributes, and PDC identity onto an item
     * while preserving player-earned enchantments and damage durability.
     */
    public static ItemStack refreshPreservingEnchants(ItemStack existing) {
        PotatoItemType type = getItemType(existing);
        if (type == null) {
            return existing;
        }
        ItemStack fresh = create(type, existing.getAmount());
        ItemMeta freshMeta = fresh.getItemMeta();
        ItemMeta oldMeta = existing.getItemMeta();
        if (oldMeta != null) {
            for (var entry : oldMeta.getEnchants().entrySet()) {
                freshMeta.addEnchant(entry.getKey(), entry.getValue(), true);
            }
            if (oldMeta instanceof org.bukkit.inventory.meta.Damageable oldDmg
                    && freshMeta instanceof org.bukkit.inventory.meta.Damageable newDmg) {
                newDmg.setDamage(oldDmg.getDamage());
            }
            String existingToken = oldMeta.getPersistentDataContainer().get(uniqueTokenKey, PersistentDataType.STRING);
            if (existingToken != null) {
                freshMeta.getPersistentDataContainer().set(uniqueTokenKey, PersistentDataType.STRING, existingToken);
            }
        }
        fresh.setItemMeta(freshMeta);
        return fresh;
    }

    public static Material getDiamondFallback(Material netherite) {
        return switch (netherite) {
            case NETHERITE_HELMET -> Material.DIAMOND_HELMET;
            case NETHERITE_CHESTPLATE -> Material.DIAMOND_CHESTPLATE;
            case NETHERITE_LEGGINGS -> Material.DIAMOND_LEGGINGS;
            case NETHERITE_BOOTS -> Material.DIAMOND_BOOTS;
            case NETHERITE_SWORD -> Material.DIAMOND_SWORD;
            case NETHERITE_AXE -> Material.DIAMOND_AXE;
            case NETHERITE_PICKAXE -> Material.DIAMOND_PICKAXE;
            case NETHERITE_SHOVEL -> Material.DIAMOND_SHOVEL;
            case NETHERITE_HOE -> Material.DIAMOND_HOE;
            default -> netherite;
        };
    }
}
