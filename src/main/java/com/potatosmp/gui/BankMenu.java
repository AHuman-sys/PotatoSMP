package com.potatosmp.gui;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.database.PlayerProfile;
import com.potatosmp.economy.EconomyService;
import com.potatosmp.economy.MarketService;
import com.potatosmp.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public final class BankMenu extends MenuHolder {

    public BankMenu(PotatoSMPPlugin plugin, Player viewer) {
        super(plugin, viewer, 27, "<gold><bold>Potato Bill's Bank</bold></gold>");
    }

    @Override
    public void render() {
        fillBackground();
        EconomyService economy = plugin.getEconomyService();
        MarketService market = plugin.getMarketService();
        PlayerProfile profile = economy.getOrLoadProfile(viewer.getUniqueId(), viewer.getName());
        EconomyService.DepositPreview allPreview = economy.previewInventoryDeposit(viewer, -1);

        // Slot 4: Account Summary
        inventory.setItem(4, createIcon(Material.SUNFLOWER,
                "<gold><bold>Your Potato Account</bold></gold>",
                List.of(
                        "<yellow>Credits Balance:</yellow> <gold><bold>" + TextUtil.formatCredits(profile.getCredits()) + "</bold></gold>",
                        "<green>Lifetime Deposited:</green> <yellow>" + TextUtil.formatCredits(profile.getLifetimePotatoes()) + " Potatoes</yellow>",
                        "<gray>Golden Potatoes Banked:</gray> <gold>" + TextUtil.formatCredits(profile.getLifetimeGoldenPotatoes()) + "</gold>"
                )));

        // Slot 10: Market Exchange Rate
        inventory.setItem(10, createIcon(Material.EMERALD,
                "<green><bold>Live Exchange Rates</bold></green>",
                List.of(
                        "<yellow>1 Potato:</yellow> <gold>" + TextUtil.formatRate(market.getRegularPotatoRate()) + " Credits</gold>",
                        "<gold>1 Golden Potato:</gold> <yellow>" + TextUtil.formatRate(market.getGoldenPotatoRate()) + " Credits</yellow>",
                        "<gray>Market Trend:</gray> " + market.getLastTrend()
                )));

        // Slot 12: Deposit All Potatoes
        inventory.setItem(12, createIcon(Material.POTATO,
                "<green><bold>Deposit All Potatoes</bold></green>",
                List.of(
                        "<gray>Detected in your inventory:</gray>",
                        "<yellow>• Regular Potatoes:</yellow> <green>" + allPreview.regularPotatoes() + "</green>",
                        "<gold>• Golden Potatoes:</gold> <yellow>" + allPreview.goldenPotatoes() + "</yellow>",
                        "",
                        "<gold>Estimated Payout:</gold> <green><bold>+" + TextUtil.formatCredits(allPreview.estimatedCredits()) + " Credits</bold></green>",
                        "",
                        "<yellow>Click to confirm deposit</yellow>"
                )));

        // Slot 14: Deposit Stack (64 regular potatoes)
        EconomyService.DepositPreview stackPreview = economy.previewInventoryDeposit(viewer, 64);
        inventory.setItem(14, createIcon(Material.BAKED_POTATO,
                "<yellow><bold>Deposit 1 Stack (Up to 64)</bold></yellow>",
                List.of(
                        "<gray>Deposit up to 64 regular potatoes.</gray>",
                        "<yellow>Available:</yellow> <green>" + stackPreview.regularPotatoes() + "</green>",
                        "<gold>Estimated Payout:</gold> <green>+" + TextUtil.formatCredits(stackPreview.estimatedCredits()) + " Credits</green>",
                        "",
                        "<yellow>Click to confirm deposit</yellow>"
                )));

        // Slot 16: Transaction History
        inventory.setItem(16, createIcon(Material.WRITABLE_BOOK,
                "<gold><bold>Transaction History</bold></gold>",
                List.of(
                        "<gray>View your recent deposits, purchases,</gray>",
                        "<gray>bounties, and player trades.</gray>",
                        "",
                        "<yellow>Click to inspect ledger</yellow>"
                )));

        // Slot 22: Close
        inventory.setItem(22, createIcon(Material.BARRIER, "<red>Close</red>", List.of()));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            return;
        }
        int slot = event.getSlot();
        if (slot == 12) {
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
            new DepositConfirmMenu(plugin, viewer, -1).open();
        } else if (slot == 14) {
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
            new DepositConfirmMenu(plugin, viewer, 64).open();
        } else if (slot == 16) {
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
            new TransactionHistoryMenu(plugin, viewer).open();
        } else if (slot == 22) {
            viewer.closeInventory();
        }
    }
}
