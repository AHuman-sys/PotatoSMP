package com.potatosmp.gui;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.bounty.BountyService;
import com.potatosmp.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BountyAmountMenu extends MenuHolder {

    private final UUID targetUuid;
    private final String targetName;
    private final Map<Integer, Long> slotToAmount = new HashMap<>();

    public BountyAmountMenu(PotatoSMPPlugin plugin, Player viewer, UUID targetUuid, String targetName) {
        super(plugin, viewer, 27, "<gold><bold>Bounty: </bold></gold><red>" + targetName + "</red>");
        this.targetUuid = targetUuid;
        this.targetName = targetName;
    }

    @Override
    public void render() {
        fillBackground();
        slotToAmount.clear();

        long balance = plugin.getEconomyService().getBalance(viewer.getUniqueId());
        inventory.setItem(4, createIcon(Material.SUNFLOWER,
                "<gold>Your Credits:</gold> <yellow>" + TextUtil.formatCredits(balance) + "</yellow>",
                List.of("<gray>Target:</gray> <red>" + targetName + "</red>")));

        List<Integer> presets = plugin.getConfigManager().getConfig().getIntegerList("bounty.quick-amounts");
        if (presets.isEmpty()) {
            presets = List.of(250, 500, 1000, 2500, 5000, 10000);
        }
        int[] slots = {10, 11, 12, 14, 15, 16};
        for (int i = 0; i < Math.min(presets.size(), slots.length); i++) {
            long amount = presets.get(i);
            inventory.setItem(slots[i], createIcon(Material.GOLD_INGOT,
                    "<gold><bold>+" + TextUtil.formatCredits(amount) + " Credits</bold></gold>",
                    List.of(
                            balance >= amount
                                    ? "<green>Click to place bounty</green>"
                                    : "<red>Insufficient Credits</red>",
                            "<gray>Custom amount:</gray> <yellow>/bounty place " + targetName + " <amount></yellow>"
                    )));
            slotToAmount.put(slots[i], amount);
        }

        inventory.setItem(22, createIcon(Material.ARROW, "<yellow>Back</yellow>", List.of()));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            return;
        }
        int slot = event.getSlot();
        if (slot == 22) {
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            new BountyMainMenu(plugin, viewer).open();
            return;
        }
        Long amount = slotToAmount.get(slot);
        if (amount != null) {
            BountyService.PlaceResult res = plugin.getBountyService().placeOrIncreaseBounty(viewer, targetUuid, targetName, amount);
            if (!res.success()) {
                viewer.sendMessage(TextUtil.mm("<red>" + res.message() + "</red>"));
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            } else {
                plugin.getScoreboardService().updatePlayer(viewer);
                new BountyMainMenu(plugin, viewer).open();
            }
        }
    }
}
