package com.potatosmp.gui;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.economy.EconomyService;
import com.potatosmp.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;
import java.util.Map;

public final class DepositConfirmMenu extends MenuHolder {

    private final int maxRegularLimit;

    public DepositConfirmMenu(PotatoSMPPlugin plugin, Player viewer, int maxRegularLimit) {
        super(plugin, viewer, 27, "<gold><bold>Confirm Potato Deposit</bold></gold>");
        this.maxRegularLimit = maxRegularLimit;
    }

    @Override
    public void render() {
        fillBackground();
        EconomyService.DepositPreview preview = plugin.getEconomyService().previewInventoryDeposit(viewer, maxRegularLimit);

        inventory.setItem(13, createIcon(Material.POTATO,
                "<gold><bold>Deposit Summary</bold></gold>",
                List.of(
                        "<yellow>Regular Potatoes:</yellow> <green>" + preview.regularPotatoes() + "</green>",
                        "<gold>Golden Potatoes:</gold> <yellow>" + preview.goldenPotatoes() + "</yellow>",
                        "<gray>Poisonous Potatoes:</gray> <yellow>" + preview.poisonousPotatoes() + "</yellow>",
                        "",
                        "<gold>Total Payout:</gold> <green><bold>+" + TextUtil.formatCredits(preview.estimatedCredits()) + " Credits</bold></green>"
                )));

        inventory.setItem(11, createIcon(Material.LIME_CONCRETE,
                "<green><bold>CONFIRM DEPOSIT</bold></green>",
                List.of(
                        "<gray>Items are verified live in your inventory</gray>",
                        "<gray>and converted into digital Credits.</gray>"
                )));

        inventory.setItem(15, createIcon(Material.RED_CONCRETE,
                "<red><bold>CANCEL</bold></red>",
                List.of("<gray>Return to Potato Bill's Bank.</gray>")));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            return;
        }
        int slot = event.getSlot();
        if (slot == 11) {
            EconomyService.DepositResult res = plugin.getEconomyService().executeDeposit(viewer, maxRegularLimit);
            if (!res.success()) {
                viewer.sendMessage(plugin.getConfigManager().msg("economy.deposit-empty"));
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                new BankMenu(plugin, viewer).open();
                return;
            }
            int totalItems = res.regularPotatoes() + res.goldenPotatoes() + res.poisonousPotatoes();
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.4f);
            viewer.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, viewer.getLocation().add(0, 1.2, 0), 15, 0.4, 0.4, 0.4, 0.05);
            viewer.sendMessage(plugin.getConfigManager().msg("economy.deposit-success", Map.of(
                    "potatoes", String.valueOf(totalItems),
                    "credits", TextUtil.formatCredits(res.creditsAwarded()),
                    "balance", TextUtil.formatCredits(res.newBalance())
            )));
            viewer.sendActionBar(TextUtil.mm("<green>+" + TextUtil.formatCredits(res.creditsAwarded()) + " Credits</green> <gray>from Potato Bill</gray>"));
            plugin.getScoreboardService().updatePlayer(viewer);
            new BankMenu(plugin, viewer).open();
        } else if (slot == 15) {
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 0.9f);
            new BankMenu(plugin, viewer).open();
        }
    }
}
