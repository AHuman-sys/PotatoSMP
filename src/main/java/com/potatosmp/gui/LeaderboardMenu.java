package com.potatosmp.gui;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.database.DatabaseManager;
import com.potatosmp.database.PlayerProfile;
import com.potatosmp.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public final class LeaderboardMenu extends MenuHolder {

    public enum Category {
        POTATOES("Most Potatoes Deposited", "lifetime_potatoes"),
        CREDITS("Richest Players", "credits"),
        BOUNTIES_CLAIMED("Highest Bounties Claimed", "bounties_claimed_count"),
        BOUNTY_EARNINGS("Most Bounty Rewards Earned", "bounty_credits_earned"),
        WEEKLY("Weekly Competition Top", "weekly");

        private final String title;
        private final String column;

        Category(String title, String column) {
            this.title = title;
            this.column = column;
        }
    }

    private Category activeCategory;

    public LeaderboardMenu(PotatoSMPPlugin plugin, Player viewer, Category activeCategory) {
        super(plugin, viewer, 54, "<gold><bold>PotatoSMP Leaderboards</bold></gold>");
        this.activeCategory = activeCategory;
    }

    @Override
    public void render() {
        fillBackground();

        // Category selector bar
        inventory.setItem(2, categoryTab(Category.POTATOES, Material.POTATO));
        inventory.setItem(3, categoryTab(Category.CREDITS, Material.GOLD_INGOT));
        inventory.setItem(4, categoryTab(Category.BOUNTIES_CLAIMED, Material.IRON_SWORD));
        inventory.setItem(5, categoryTab(Category.BOUNTY_EARNINGS, Material.EMERALD));
        inventory.setItem(6, categoryTab(Category.WEEKLY, Material.CLOCK));

        int[] slots = {
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };

        if (activeCategory == Category.WEEKLY) {
            List<DatabaseManager.WeeklyEntry> entries = plugin.getCompetitionService().getTopEntries(14);
            var week = plugin.getCompetitionService().getCurrentWeek();
            String endsIn = week != null
                    ? TextUtil.formatDurationSeconds((week.endsAt() - System.currentTimeMillis()) / 1000L)
                    : "N/A";
            inventory.setItem(13, createIcon(Material.SUNFLOWER, "<gold><bold>Weekly Harvest Race</bold></gold>",
                    List.of("<yellow>Ends in:</yellow> <green>" + endsIn + "</green>")));
            for (int i = 0; i < Math.min(entries.size(), slots.length); i++) {
                var e = entries.get(i);
                inventory.setItem(slots[i], playerHead(e.playerUuid(), e.playerName(),
                        "<gold>#" + (i + 1) + " <yellow>" + e.playerName() + "</yellow></gold>",
                        List.of("<green>Weekly Potatoes:</green> <yellow>" + TextUtil.formatCredits(e.potatoesDeposited()) + "</yellow>")));
            }
        } else {
            List<PlayerProfile> profiles = plugin.getEconomyService().getLeaderboard(activeCategory.column, 14);
            for (int i = 0; i < Math.min(profiles.size(), slots.length); i++) {
                PlayerProfile p = profiles.get(i);
                String metricLine = switch (activeCategory) {
                    case POTATOES -> "<green>Potatoes Deposited:</green> <yellow>" + TextUtil.formatCredits(p.getLifetimePotatoes()) + "</yellow>";
                    case CREDITS -> "<gold>Credits:</gold> <yellow>" + TextUtil.formatCredits(p.getCredits()) + "</yellow>";
                    case BOUNTIES_CLAIMED -> "<red>Bounties Claimed:</red> <yellow>" + p.getBountiesClaimedCount() + "</yellow>";
                    case BOUNTY_EARNINGS -> "<gold>Bounty Earnings:</gold> <yellow>" + TextUtil.formatCredits(p.getBountyCreditsEarned()) + " Credits</yellow>";
                    default -> "";
                };
                inventory.setItem(slots[i], playerHead(p.getUuid(), p.getLastKnownName(),
                        "<gold>#" + (i + 1) + " <yellow>" + p.getLastKnownName() + "</yellow></gold>",
                        List.of(metricLine)));
            }
        }

        inventory.setItem(49, createIcon(Material.BARRIER, "<red>Close</red>", List.of()));
    }

    private ItemStack categoryTab(Category cat, Material mat) {
        boolean selected = cat == activeCategory;
        return createIcon(mat,
                (selected ? "<green><bold>▶ " : "<gold>") + cat.title + (selected ? "</bold></green>" : "</gold>"),
                List.of(selected ? "<yellow>Currently viewing</yellow>" : "<gray>Click to view</gray>"));
    }

    private ItemStack playerHead(java.util.UUID uuid, String name, String display, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            meta.displayName(TextUtil.mm(display));
            meta.lore(TextUtil.mmList(lore));
            head.setItemMeta(meta);
        }
        return head;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            return;
        }
        int slot = event.getSlot();
        Category next = switch (slot) {
            case 2 -> Category.POTATOES;
            case 3 -> Category.CREDITS;
            case 4 -> Category.BOUNTIES_CLAIMED;
            case 5 -> Category.BOUNTY_EARNINGS;
            case 6 -> Category.WEEKLY;
            default -> null;
        };
        if (next != null && next != activeCategory) {
            activeCategory = next;
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.15f);
            render();
        } else if (slot == 49) {
            viewer.closeInventory();
        }
    }
}
