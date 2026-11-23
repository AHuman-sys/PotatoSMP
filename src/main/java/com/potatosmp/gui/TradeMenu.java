package com.potatosmp.gui;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.trade.TradeService;
import com.potatosmp.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class TradeMenu extends MenuHolder {

    private static final int[] MY_SLOTS = {
            0, 1, 2,
            9, 10, 11,
            18, 19, 20,
            27, 28, 29
    };

    private static final int[] PARTNER_SLOTS = {
            6, 7, 8,
            15, 16, 17,
            24, 25, 26,
            33, 34, 35
    };

    private final TradeService.TradeSession session;

    public TradeMenu(PotatoSMPPlugin plugin, Player viewer, TradeService.TradeSession session) {
        super(plugin, viewer, 54, "<gold><bold>Secure PotatoSMP Trade</bold></gold>");
        this.session = session;
    }

    @Override
    public void render() {
        fillBackground();
        boolean isA = session.isA(viewer.getUniqueId());
        ItemStack[] myOffer = isA ? session.offerA : session.offerB;
        ItemStack[] partnerOffer = isA ? session.offerB : session.offerA;
        long myCredits = isA ? session.creditsA : session.creditsB;
        long partnerCredits = isA ? session.creditsB : session.creditsA;
        boolean myReady = isA ? session.readyA : session.readyB;
        boolean partnerReady = isA ? session.readyB : session.readyA;
        Player partner = Bukkit.getPlayer(session.partnerOf(viewer.getUniqueId()));
        String partnerName = partner != null ? partner.getName() : "Partner";

        for (int i = 0; i < 12; i++) {
            inventory.setItem(MY_SLOTS[i], myOffer[i]);
            inventory.setItem(PARTNER_SLOTS[i], partnerOffer[i]);
        }

        // Divider column
        ItemStack divider = createIcon(Material.YELLOW_STAINED_GLASS_PANE, "<gold>│</gold>", List.of());
        for (int s : new int[]{4, 13, 22, 31, 40}) {
            inventory.setItem(s, divider);
        }

        long balance = plugin.getEconomyService().getBalance(viewer.getUniqueId());
        inventory.setItem(37, createIcon(Material.GOLD_INGOT,
                "<gold><bold>Your Credit Offer:</bold></gold> <yellow>" + TextUtil.formatCredits(myCredits) + "</yellow>",
                List.of(
                        "<gray>Your Balance:</gray> <yellow>" + TextUtil.formatCredits(balance) + "</yellow>",
                        "",
                        "<green>Left-Click:</green> <yellow>+100 Credits</yellow>",
                        "<green>Shift + Left:</green> <yellow>+1,000 Credits</yellow>",
                        "<red>Right-Click:</red> <yellow>-100 Credits</yellow>",
                        "<red>Shift + Right:</red> <yellow>-1,000 Credits</yellow>"
                )));

        inventory.setItem(39, createIcon(myReady ? Material.LIME_WOOL : Material.RED_WOOL,
                myReady ? "<green><bold>READY (LOCKED)</bold></green>" : "<yellow><bold>CLICK TO LOCK IN</bold></yellow>",
                List.of("<gray>Both players must lock in to complete trade.</gray>")));

        inventory.setItem(41, createIcon(Material.BARRIER, "<red><bold>CANCEL TRADE</bold></red>",
                List.of("<gray>Safely returns all offered items.</gray>")));

        inventory.setItem(43, createIcon(partnerReady ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                "<gold>" + partnerName + "'s Offer</gold>",
                List.of(
                        "<yellow>Credits:</yellow> <gold>" + TextUtil.formatCredits(partnerCredits) + "</gold>",
                        "<gray>Status:</gray> " + (partnerReady ? "<green>READY</green>" : "<red>Not Ready</red>")
                )));

        String banner = session.countdown > 0
                ? "<green><bold>Completing in " + session.countdown + "s...</bold></green>"
                : "<yellow>Click items in your inventory to offer</yellow>";
        inventory.setItem(49, createIcon(Material.PAPER, banner, List.of()));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (session.finished) {
            return;
        }
        TradeService tradeService = plugin.getTradeService();
        boolean isA = session.isA(viewer.getUniqueId());
        ItemStack[] myOffer = isA ? session.offerA : session.offerB;

        if (event.getClickedInventory() != null && event.getClickedInventory().equals(viewer.getInventory())) {
            // Player clicked their own inventory -> move stack to trade offer
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) {
                return;
            }
            for (int i = 0; i < myOffer.length; i++) {
                if (myOffer[i] == null || myOffer[i].getType().isAir()) {
                    myOffer[i] = clicked.clone();
                    event.setCurrentItem(null);
                    viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                    tradeService.resetReady(session);
                    return;
                }
            }
            return;
        }

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            return;
        }

        int slot = event.getSlot();
        for (int i = 0; i < MY_SLOTS.length; i++) {
            if (MY_SLOTS[i] == slot && myOffer[i] != null && !myOffer[i].getType().isAir()) {
                if (viewer.getInventory().firstEmpty() == -1) {
                    return;
                }
                ItemStack back = myOffer[i].clone();
                myOffer[i] = null;
                viewer.getInventory().addItem(back);
                viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.9f);
                tradeService.resetReady(session);
                return;
            }
        }

        if (slot == 37) {
            long balance = plugin.getEconomyService().getBalance(viewer.getUniqueId());
            long delta = event.isShiftClick() ? 1000L : 100L;
            if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                delta = -delta;
            }
            long current = isA ? session.creditsA : session.creditsB;
            long updated = Math.max(0L, Math.min(balance, current + delta));
            if (isA) {
                session.creditsA = updated;
            } else {
                session.creditsB = updated;
            }
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.3f);
            tradeService.resetReady(session);
        } else if (slot == 39) {
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.3f);
            tradeService.toggleReady(viewer, session);
        } else if (slot == 41) {
            tradeService.cancelTrade(session, "trade.cancelled");
        }
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (!session.finished) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getTradeService().cancelTrade(session, "trade.cancelled"));
        }
    }
}
