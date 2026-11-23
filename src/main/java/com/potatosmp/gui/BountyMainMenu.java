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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BountyMainMenu extends MenuHolder {

    private final Map<Integer, BountyRecord> slotToBounty = new HashMap<>();

    public BountyMainMenu(PotatoSMPPlugin plugin, Player viewer) {
        super(plugin, viewer, 54, "<gold><bold>PotatoSMP Bounty Board</bold></gold>");
    }

    @Override
    public void render() {
        fillBackground();
        slotToBounty.clear();

        // Top controls
        inventory.setItem(2, createIcon(Material.PLAYER_HEAD,
                "<green><bold>Place / Increase Bounty</bold></gold>",
                List.of("<gray>Select a player to place a</gray>", "<gray>Credit bounty on their head.</gray>", "", "<yellow>Click to choose target</yellow>")));

        inventory.setItem(4, createIcon(Material.WRITABLE_BOOK,
                "<gold><bold>My Placed Bounties</bold></gold>",
                List.of("<gray>Inspect bounties you funded.</gray>")));

        inventory.setItem(6, createIcon(Material.SKELETON_SKULL,
                "<yellow><bold>Completed Bounties</bold></yellow>",
                List.of("<gray>Browse recently claimed hits.</gray>")));

        // Active bounties leaderboard in middle grid
        List<BountyRecord> active = plugin.getBountyService().getActiveBounties(21);
        int[] slots = {
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        if (active.isEmpty()) {
            inventory.setItem(31, createIcon(Material.PAPER, "<yellow>No Active Bounties</yellow>",
                    List.of("<gray>Click 'Place / Increase Bounty' above</gray>", "<gray>or use /bounty place <player> <amount></gray>")));
        } else {
            long now = System.currentTimeMillis();
            for (int i = 0; i < Math.min(active.size(), slots.length); i++) {
                BountyRecord b = active.get(i);
                long remSec = (b.expiresAt() - now) / 1000L;
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(b.targetUuid()));
                    meta.displayName(TextUtil.mm("<gold>#" + (i + 1) + " <red>" + b.targetName() + "</red></gold>"));
                    meta.lore(TextUtil.mmList(List.of(
                            "<yellow>Reward:</yellow> <gold><bold>" + TextUtil.formatCredits(b.totalAmount()) + " Credits</bold></gold>",
                            "<gray>Expires in:</gray> <yellow>" + TextUtil.formatDurationSeconds(remSec) + "</yellow>",
                            "",
                            "<green>Click to increase this bounty!</green>"
                    )));
                    head.setItemMeta(meta);
                }
                inventory.setItem(slots[i], head);
                slotToBounty.put(slots[i], b);
            }
        }

        inventory.setItem(49, createIcon(Material.BARRIER, "<red>Close</red>", List.of()));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            return;
        }
        int slot = event.getSlot();
        if (slot == 2) {
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
            new BountyTargetSelectMenu(plugin, viewer).open();
        } else if (slot == 4) {
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
            new BountyListMenu(plugin, viewer, BountyListMenu.Mode.PLACED).open();
        } else if (slot == 6) {
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
            new BountyListMenu(plugin, viewer, BountyListMenu.Mode.COMPLETED).open();
        } else if (slot == 49) {
            viewer.closeInventory();
        } else if (slotToBounty.containsKey(slot)) {
            BountyRecord rec = slotToBounty.get(slot);
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
            new BountyAmountMenu(plugin, viewer, rec.targetUuid(), rec.targetName()).open();
        }
    }
}
