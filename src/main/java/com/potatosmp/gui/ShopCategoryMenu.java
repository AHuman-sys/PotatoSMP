package com.potatosmp.gui;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ShopCategoryMenu extends MenuHolder {

    private final Map<Integer, String> slotToCategory = new HashMap<>();

    public ShopCategoryMenu(PotatoSMPPlugin plugin, Player viewer) {
        super(plugin, viewer, 27, "<gold><bold>PotatoSMP Shop</bold></gold>");
    }

    @Override
    public void render() {
        fillBackground();
        slotToCategory.clear();
        long balance = plugin.getEconomyService().getBalance(viewer.getUniqueId());

        inventory.setItem(4, createIcon(Material.SUNFLOWER,
                "<gold><bold>Your Credits:</bold></gold> <yellow>" + TextUtil.formatCredits(balance) + "</yellow>",
                List.of("<gray>Earn Credits by banking potatoes</gray>", "<gray>with Potato Bill at spawn.</gray>")));

        ConfigurationSection categories = plugin.getConfigManager().getShopConfig().getConfigurationSection("categories");
        if (categories != null) {
            for (String key : categories.getKeys(false)) {
                int slot = categories.getInt(key + ".slot", 11);
                String title = categories.getString(key + ".title", "<gold>" + key + "</gold>");
                Material icon = Material.matchMaterial(categories.getString(key + ".icon", "POTATO"));
                if (icon == null) {
                    icon = Material.POTATO;
                }
                List<String> lore = new ArrayList<>(categories.getStringList(key + ".description"));
                lore.add("");
                lore.add("<yellow>Click to browse category</yellow>");
                inventory.setItem(slot, createIcon(icon, title, lore));
                slotToCategory.put(slot, key);
            }
        }

        inventory.setItem(22, createIcon(Material.BARRIER, "<red>Close</red>", List.of()));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            return;
        }
        int slot = event.getSlot();
        if (slot == 22) {
            viewer.closeInventory();
            return;
        }
        String cat = slotToCategory.get(slot);
        if (cat != null) {
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
            new ShopItemsMenu(plugin, viewer, cat).open();
        }
    }
}
