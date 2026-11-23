package com.potatosmp.gui;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.database.TransactionRecord;
import com.potatosmp.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public final class TransactionHistoryMenu extends MenuHolder {

    public TransactionHistoryMenu(PotatoSMPPlugin plugin, Player viewer) {
        super(plugin, viewer, 54, "<gold><bold>Bank Transaction Ledger</bold></gold>");
    }

    @Override
    public void render() {
        fillBackground();
        List<TransactionRecord> records = plugin.getEconomyService().getRecentTransactions(viewer.getUniqueId(), 28);
        int[] innerSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        if (records.isEmpty()) {
            inventory.setItem(22, createIcon(Material.PAPER, "<yellow>No Transactions Yet</yellow>",
                    List.of("<gray>Deposit potatoes at Potato Bill to begin!</gray>")));
        } else {
            for (int i = 0; i < Math.min(records.size(), innerSlots.length); i++) {
                TransactionRecord tx = records.get(i);
                String sign = tx.creditDelta() >= 0 ? "<green>+" : "<red>";
                Material icon = tx.creditDelta() >= 0 ? Material.GOLD_INGOT : Material.PAPER;
                inventory.setItem(innerSlots[i], createIcon(icon,
                        "<gold>" + tx.type().getDisplayName() + "</gold>",
                        List.of(
                                "<yellow>Credits:</yellow> " + sign + TextUtil.formatCredits(tx.creditDelta()) + "</reset>",
                                "<gray>Potatoes:</gray> <yellow>" + tx.potatoCount() + "</yellow>",
                                "<gray>Details:</gray> <white>" + tx.details() + "</white>",
                                "<dark_gray>" + TextUtil.formatTimestamp(tx.createdAt()) + "</dark_gray>"
                        )));
            }
        }

        inventory.setItem(49, createIcon(Material.ARROW, "<yellow>Back to Bank</yellow>", List.of()));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(inventory) && event.getSlot() == 49) {
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            new BankMenu(plugin, viewer).open();
        }
    }
}
