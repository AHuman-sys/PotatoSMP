package com.potatosmp.items;

import org.bukkit.Material;

public enum PotatoItemType {
    POTATO_HELMET("potato_helmet", Material.LEATHER_HELMET, "<gold><bold>Potato Crown Helmet</bold></gold>", false),
    POTATO_CHESTPLATE("potato_chestplate", Material.LEATHER_CHESTPLATE, "<gold><bold>Potato Carapace Chestplate</bold></gold>", false),
    POTATO_LEGGINGS("potato_leggings", Material.LEATHER_LEGGINGS, "<gold><bold>Potato Greaves</bold></gold>", false),
    POTATO_BOOTS("potato_boots", Material.LEATHER_BOOTS, "<gold><bold>Potato Strider Boots</bold></gold>", false),
    POTATO_SWORD("potato_sword", Material.IRON_SWORD, "<yellow><bold>Spud Striker Sword</bold></yellow>", false),
    POTATO_AXE("potato_axe", Material.IRON_AXE, "<gold><bold>Crusher Potato Cleaver</bold></gold>", false),
    POTATO_PICKAXE("potato_pickaxe", Material.DIAMOND_PICKAXE, "<gold><bold>Baked Quarry Pickaxe</bold></gold>", false),
    POTATO_HOE("potato_hoe", Material.DIAMOND_HOE, "<green><bold>Harvest Master Hoe</bold></green>", false),
    POTATO_HEART("potato_heart", Material.APPLE, "<gold><bold>Potato Heart</bold></gold>", true),
    SPUD_SEEKER("spud_seeker", Material.GOLDEN_HELMET, "<yellow><bold>Bounty Hat: The Spud Seeker</bold></yellow>", false),
    GOLDEN_POTATO("golden_potato", Material.POTATO, "<gold><bold>Golden Potato</bold></gold>", false),
    GOLDEN_BAKED_POTATO("golden_baked_potato", Material.BAKED_POTATO, "<gold><bold>Golden Baked Potato</bold></gold>", false);

    private final String id;
    private final Material material;
    private final String defaultDisplayName;
    private final boolean uniqueToken;

    PotatoItemType(String id, Material material, String defaultDisplayName, boolean uniqueToken) {
        this.id = id;
        this.material = material;
        this.defaultDisplayName = defaultDisplayName;
        this.uniqueToken = uniqueToken;
    }

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public String getDefaultDisplayName() {
        return defaultDisplayName;
    }

    public boolean isUniqueToken() {
        return uniqueToken;
    }

    public static PotatoItemType fromId(String id) {
        if (id == null) {
            return null;
        }
        for (PotatoItemType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
