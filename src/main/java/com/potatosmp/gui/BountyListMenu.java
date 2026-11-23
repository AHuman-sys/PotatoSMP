package com.potatosmp.gui;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.database.BountyRecord;
import com.potatosmp.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public final class BountyListMenu extends MenuHolder {

    public enum Mode {
        PLACED("<gold><bold>My Placed Bounties</bold></gold>"),
        COMPLETED("<gold><bold>Completed Bounties</bold></gold>");

        private final String title;

        Mode(String title) {
            this.title = title;
        }
    }

    private final Mode mode;

    public BountyListMenu(PotatoSMPPlugin plugin, Player viewer, Mode mode) {
        super(plugin, viewer, 54, mode.title);
        this.mode = mode;
    }

    @Override
    public void render() {
        fillBackground();
        List<BountyRecord> list = mode == Mode.PLACED
                ? plugin.getBountyService().getPlacedBounties(viewer.getUniqueId(), 28)
                : plugin.getBountyService().getCompletedBounties(28);

        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        if (list.isEmpty()) {
            inventory.setItem(22, createIcon(Material.PAPER, "<yellow>No Records Found</yellow>", List.of()));
        } else {
            for (int i = 0; i < Math.min(list.size(), slots.length); i++) {
                BountyRecord b = list.get(i);
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(b.targetUuid()));
                    meta.displayName(TextUtil.mm("<red>" + b.targetName() + "</red> <gray>(" + b.status() + ")</gray>"));
                    List<String> lore = new ArrayList<>();
                    lore.add("<gold>Total Bounty:</gold> <yellow>" + TextUtil.formatCredits(b.totalAmount()) + " Credits</yellow>");
                    lore.add("<gray>Created:</gray> <white>" + TextUtil.formatTimestamp(b.createdAt()) + "</white>");
                    if (b.claimedByName() != null && b.claimedAt() != null) {
                        lore.add("<green>Claimed By:</green> <yellow>" + b.claimedByName() + "</yellow>");
                        lore.add("<gray>Claimed:</gray> <white>" + TextUtil.formatTimestamp(b.claimedAt()) + "</white>");
                    }
                    meta.lore(TextUtil.mmList(lore));
                    head.setItemMeta(meta);
                }
                inventory.setItem(slots[i], head);
            }
        }

        inventory.setItem(49, createIcon(Material.ARROW, "<yellow>Back to Bounty Board</yellow>", List.of()));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(inventory) && event.getSlot() == 49) {
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            new BountyMainMenu(plugin, viewer).open();
        }
    }
}
