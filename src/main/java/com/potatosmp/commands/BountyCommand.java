package com.potatosmp.commands;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.bounty.BountyService;
import com.potatosmp.database.BountyRecord;
import com.potatosmp.gui.BountyMainMenu;
import com.potatosmp.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public final class BountyCommand implements CommandExecutor, TabCompleter {

    private final PotatoSMPPlugin plugin;

    public BountyCommand(PotatoSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getConfigManager().isFeatureEnabled("bounties")) {
            sender.sendMessage(plugin.getConfigManager().msg("general.feature-disabled"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().msg("general.player-only"));
            return true;
        }
        if (!player.hasPermission("potatosmp.bounty")) {
            player.sendMessage(plugin.getConfigManager().msg("general.no-permission"));
            return true;
        }

        if (args.length == 0) {
            new BountyMainMenu(plugin, player).open();
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            List<BountyRecord> list = plugin.getBountyService().getActiveBounties(10);
            player.sendMessage(TextUtil.mm("<gold><bold>=== Active Bounties ===</bold></gold>"));
            if (list.isEmpty()) {
                player.sendMessage(TextUtil.mm("<gray>No active bounties at the moment.</gray>"));
            } else {
                for (int i = 0; i < list.size(); i++) {
                    BountyRecord b = list.get(i);
                    player.sendMessage(TextUtil.mm("<gold>#" + (i + 1) + "</gold> <red>" + b.targetName()
                            + "</red> <yellow>- " + TextUtil.formatCredits(b.totalAmount()) + " Credits</yellow>"));
                }
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("place") && args.length >= 3) {
            OfflinePlayer target = plugin.getBountyService().resolveOfflineTarget(args[1]);
            if (target == null) {
                player.sendMessage(plugin.getConfigManager().msg("general.player-not-found", Map.of("player", args[1])));
                return true;
            }
            long amount;
            try {
                amount = Long.parseLong(args[2]);
            } catch (NumberFormatException ex) {
                player.sendMessage(plugin.getConfigManager().msg("general.invalid-amount"));
                return true;
            }
            String targetName = target.getName() != null ? target.getName() : args[1];
            BountyService.PlaceResult res = plugin.getBountyService().placeOrIncreaseBounty(player, target.getUniqueId(), targetName, amount);
            if (!res.success()) {
                player.sendMessage(TextUtil.mm("<red>" + res.message() + "</red>"));
            } else {
                plugin.getScoreboardService().updatePlayer(player);
            }
            return true;
        }

        new BountyMainMenu(plugin, player).open();
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("place", "list");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("place")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("place")) {
            return List.of("250", "500", "1000", "2500", "5000");
        }
        return List.of();
    }
}
