package com.potatosmp.gui;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BountyTargetSelectMenu extends MenuHolder {

    private final Map<Integer, Player> slotToTarget = new HashMap<>();

    public BountyTargetSelectMenu(PotatoSMPPlugin plugin, Player viewer) {
        super(plugin, viewer, 54, "<gold><bold>Select Bounty Target</bold></gold>");
    }

    @Override
    public void render() {
        fillBackground();
        slotToTarget.clear();
        boolean allowSelf = plugin.getConfigManager().getConfig().getBoolean("bounty.allow-self-bounty", false);

        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        int idx = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!allowSelf && target.getUniqueId().equals(viewer.getUniqueId())) {
                continue;
            }
            if (idx >= slots.length) {
                break;
            }
            int slot = slots[idx++];
            var active = plugin.getBountyService().getActiveBounty(target.getUniqueId());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.displayName(TextUtil.mm("<yellow><bold>" + target.getName() + "</bold></yellow>"));
                meta.lore(TextUtil.mmList(List.of(
                        active.isPresent()
                                ? "<gold>Current Bounty:</gold> <red>" + TextUtil.formatCredits(active.get().totalAmount()) + " Credits</red>"
                                : "<gray>No active bounty</gray>",
                        "",
                        "<green>Click to set bounty amount</green>"
                )));
                head.setItemMeta(meta);
            }
            inventory.setItem(slot, head);
            slotToTarget.put(slot, target);
        }

        if (idx == 0) {
            inventory.setItem(22, createIcon(Material.PAPER, "<yellow>No Other Players Online</yellow>",
                    List.of("<gray>Use <yellow>/bounty place <player> <amount></yellow></gray>",
                            "<gray>to target offline players.</gray>")));
        }

        inventory.setItem(49, createIcon(Material.ARROW, "<yellow>Back</yellow>", List.of()));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            return;
        }
        int slot = event.getSlot();
        if (slot == 49) {
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            new BountyMainMenu(plugin, viewer).open();
            return;
        }
        Player target = slotToTarget.get(slot);
        if (target != null) {
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
            new BountyAmountMenu(plugin, viewer, target.getUniqueId(), target.getName()).open();
        }
    }
}
