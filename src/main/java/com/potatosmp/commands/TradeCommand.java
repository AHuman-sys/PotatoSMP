package com.potatosmp.commands;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TradeCommand implements CommandExecutor, TabCompleter {

    private final PotatoSMPPlugin plugin;

    public TradeCommand(PotatoSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getConfigManager().isFeatureEnabled("player-trading")) {
            sender.sendMessage(plugin.getConfigManager().msg("general.feature-disabled"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().msg("general.player-only"));
            return true;
        }
        if (!player.hasPermission("potatosmp.trade")) {
            player.sendMessage(plugin.getConfigManager().msg("general.no-permission"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(TextUtil.mm("<gold>Usage:</gold> <yellow>/trade <player|accept|decline></yellow>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("accept")) {
            plugin.getTradeService().acceptRequest(player, args.length > 1 ? args[1] : null);
            return true;
        }
        if (args[0].equalsIgnoreCase("decline")) {
            plugin.getTradeService().declineRequest(player);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(plugin.getConfigManager().msg("general.player-not-found", Map.of("player", args[0])));
            return true;
        }
        plugin.getTradeService().sendRequest(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>();
            opts.add("accept");
            opts.add("decline");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getName().equals(sender.getName())) {
                    opts.add(p.getName());
                }
            }
            return opts;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
