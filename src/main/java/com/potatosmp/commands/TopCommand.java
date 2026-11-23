package com.potatosmp.commands;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.gui.LeaderboardMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public final class TopCommand implements CommandExecutor, TabCompleter {

    private final PotatoSMPPlugin plugin;

    public TopCommand(PotatoSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().msg("general.player-only"));
            return true;
        }
        if (!player.hasPermission("potatosmp.top")) {
            player.sendMessage(plugin.getConfigManager().msg("general.no-permission"));
            return true;
        }

        LeaderboardMenu.Category category = LeaderboardMenu.Category.POTATOES;
        if (args.length > 0) {
            category = switch (args[0].toLowerCase()) {
                case "credits", "bal", "rich" -> LeaderboardMenu.Category.CREDITS;
                case "claims", "bounties" -> LeaderboardMenu.Category.BOUNTIES_CLAIMED;
                case "earnings", "rewards" -> LeaderboardMenu.Category.BOUNTY_EARNINGS;
                case "weekly", "competition" -> LeaderboardMenu.Category.WEEKLY;
                default -> LeaderboardMenu.Category.POTATOES;
            };
        }
        new LeaderboardMenu(plugin, player, category).open();
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("potatoes", "credits", "claims", "earnings", "weekly");
        }
        return List.of();
    }
}
