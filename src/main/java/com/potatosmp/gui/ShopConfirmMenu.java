package com.potatosmp.gui;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.database.TransactionType;
import com.potatosmp.items.ItemFactory;
import com.potatosmp.items.PotatoItemType;
import com.potatosmp.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public final class ShopConfirmMenu extends MenuHolder {

    private final String categoryKey;
    private final PotatoItemType itemType;
    private final long price;

    public ShopConfirmMenu(PotatoSMPPlugin plugin, Player viewer, String categoryKey, PotatoItemType itemType, long price) {
        super(plugin, viewer, 27, "<gold><bold>Confirm Purchase</bold></gold>");
        this.categoryKey = categoryKey;
        this.itemType = itemType;
        this.price = price;
    }

    @Override
    public void render() {
        fillBackground();
        long balance = plugin.getEconomyService().getBalance(viewer.getUniqueId());

        inventory.setItem(13, ItemFactory.create(itemType));

        inventory.setItem(11, createIcon(Material.LIME_CONCRETE,
                "<green><bold>CONFIRM PURCHASE</bold></green>",
                List.of(
                        "<yellow>Price:</yellow> <gold>" + TextUtil.formatCredits(price) + " Credits</gold>",
                        "<gray>Your Balance:</gray> <yellow>" + TextUtil.formatCredits(balance) + "</yellow>",
                        balance >= price
                                ? "<green>Balance After:</green> <yellow>" + TextUtil.formatCredits(balance - price) + "</yellow>"
                                : "<red>Not enough Credits!</red>"
                )));

        inventory.setItem(15, createIcon(Material.RED_CONCRETE,
                "<red><bold>CANCEL</bold></red>",
                List.of("<gray>Return to shop items.</gray>")));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            return;
        }
        int slot = event.getSlot();
        if (slot == 15) {
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 0.9f);
            new ShopItemsMenu(plugin, viewer, categoryKey).open();
            return;
        }
        if (slot == 11) {
            if (viewer.getInventory().firstEmpty() == -1) {
                viewer.sendMessage(plugin.getConfigManager().msg("shop.inventory-full"));
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                return;
            }
            long liveBalance = plugin.getEconomyService().getBalance(viewer.getUniqueId());
            if (liveBalance < price) {
                viewer.sendMessage(plugin.getConfigManager().msg("shop.insufficient-credits", Map.of(
                        "price", TextUtil.formatCredits(price),
                        "item", itemType.name(),
                        "balance", TextUtil.formatCredits(liveBalance)
                )));
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                return;
            }
            boolean withdrawn = plugin.getEconomyService().withdrawCredits(
                    viewer.getUniqueId(), viewer.getName(), price,
                    TransactionType.SHOP_BUY, "Purchased " + itemType.getId()
            );
            if (!withdrawn) {
                viewer.sendMessage(plugin.getConfigManager().msg("shop.insufficient-credits", Map.of(
                        "price", TextUtil.formatCredits(price),
                        "item", itemType.name(),
                        "balance", TextUtil.formatCredits(plugin.getEconomyService().getBalance(viewer.getUniqueId()))
                )));
                return;
            }

            ItemStack bought = ItemFactory.create(itemType);
            var leftovers = viewer.getInventory().addItem(bought);
            leftovers.values().forEach(d -> viewer.getWorld().dropItemNaturally(viewer.getLocation(), d));

            viewer.playSound(viewer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.3f);
            viewer.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, viewer.getLocation().add(0, 1.2, 0), 12, 0.35, 0.35, 0.35, 0.05);
            viewer.sendMessage(plugin.getConfigManager().msg("shop.purchased", Map.of(
                    "item", itemType.getId().replace('_', ' '),
                    "price", TextUtil.formatCredits(price)
            )));
            plugin.getScoreboardService().updatePlayer(viewer);
            new ShopItemsMenu(plugin, viewer, categoryKey).open();
        }
    }
}
