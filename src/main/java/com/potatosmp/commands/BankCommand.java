package com.potatosmp.commands;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.gui.BankMenu;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class BankCommand implements CommandExecutor {

    private final PotatoSMPPlugin plugin;

    public BankCommand(PotatoSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().msg("general.player-only"));
            return true;
        }
        if (!player.hasPermission("potatosmp.bank")) {
            player.sendMessage(plugin.getConfigManager().msg("general.no-permission"));
            return true;
        }

        if (!plugin.getConfigManager().isFeatureEnabled("bank-remote-command")) {
            Location npcLoc = plugin.getConfigManager().getLocation("bank.npc");
            if (npcLoc != null && (!player.getWorld().equals(npcLoc.getWorld())
                    || player.getLocation().distanceSquared(npcLoc) > 100.0)) {
                String coords = npcLoc.getBlockX() + ", " + npcLoc.getBlockY() + ", " + npcLoc.getBlockZ();
                player.sendMessage(plugin.getConfigManager().msg("economy.bank-too-far", Map.of("coords", coords)));
                return true;
            }
        }

        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.8f, 1.15f);
        new BankMenu(plugin, player).open();
        return true;
    }
}
