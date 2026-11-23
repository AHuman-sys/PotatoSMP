package com.potatosmp.gui;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.items.ItemFactory;
import com.potatosmp.items.PotatoItemType;
import com.potatosmp.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ShopItemsMenu extends MenuHolder {

    private final String categoryKey;
    private final Map<Integer, PotatoItemType> slotToItem = new HashMap<>();

    public ShopItemsMenu(PotatoSMPPlugin plugin, Player viewer, String categoryKey) {
        super(plugin, viewer, 27, plugin.getConfigManager().getShopConfig()
                .getString("categories." + categoryKey + ".title", "<gold><bold>Potato Shop</bold></gold>"));
        this.categoryKey = categoryKey;
    }

    @Override
    public void render() {
        fillBackground();
        slotToItem.clear();

        long balance = plugin.getEconomyService().getBalance(viewer.getUniqueId());
        inventory.setItem(4, createIcon(Material.SUNFLOWER,
                "<gold>Credits:</gold> <yellow>" + TextUtil.formatCredits(balance) + "</yellow>",
                List.of()));

        ConfigurationSection itemsSection = plugin.getConfigManager().getShopConfig().getConfigurationSection("items");
        if (itemsSection != null) {
            for (String itemKey : itemsSection.getKeys(false)) {
                if (!itemsSection.getBoolean(itemKey + ".enabled", true)) {
                    continue;
                }
                if (!categoryKey.equalsIgnoreCase(itemsSection.getString(itemKey + ".category"))) {
                    continue;
                }
                PotatoItemType type;
                try {
                    type = PotatoItemType.valueOf(itemKey);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                int slot = itemsSection.getInt(itemKey + ".slot", 10);
                long price = itemsSection.getLong(itemKey + ".price", 1000L);

                ItemStack display = ItemFactory.create(type);
                ItemMeta meta = display.getItemMeta();
                List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(Component.empty());
                lore.add(TextUtil.mm("<gold>Price:</gold> <yellow><bold>" + TextUtil.formatCredits(price) + " Credits</bold></yellow>"));
                lore.add(balance >= price
                        ? TextUtil.mm("<green>Click to purchase</green>")
                        : TextUtil.mm("<red>Insufficient Credits</red>"));
                meta.lore(lore);
                display.setItemMeta(meta);

                inventory.setItem(slot, display);
                slotToItem.put(slot, type);
            }
        }

        inventory.setItem(22, createIcon(Material.ARROW, "<yellow>Back to Categories</yellow>", List.of()));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            return;
        }
        int slot = event.getSlot();
        if (slot == 22) {
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            new ShopCategoryMenu(plugin, viewer).open();
            return;
        }
        PotatoItemType type = slotToItem.get(slot);
        if (type != null) {
            long price = plugin.getConfigManager().getShopConfig().getLong("items." + type.name() + ".price", 1000L);
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
            new ShopConfirmMenu(plugin, viewer, categoryKey, type, price).open();
        }
    }
}
