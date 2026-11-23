package com.potatosmp.commands;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.database.PlayerProfile;
import com.potatosmp.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BalanceCommand implements CommandExecutor, TabCompleter {

    private final PotatoSMPPlugin plugin;

    public BalanceCommand(PotatoSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && sender.hasPermission("potatosmp.balance.others")) {
            Optional<PlayerProfile> target = plugin.getEconomyService().findByName(args[0]);
            if (target.isEmpty()) {
                sender.sendMessage(plugin.getConfigManager().msg("general.player-not-found", Map.of("player", args[0])));
                return true;
            }
            PlayerProfile p = target.get();
            sender.sendMessage(plugin.getConfigManager().msg("economy.balance-other", Map.of(
                    "player", p.getLastKnownName(),
                    "credits", TextUtil.formatCredits(p.getCredits()),
                    "potatoes", TextUtil.formatCredits(p.getLifetimePotatoes())
            )));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().msg("general.player-only"));
            return true;
        }
        if (!player.hasPermission("potatosmp.balance")) {
            player.sendMessage(plugin.getConfigManager().msg("general.no-permission"));
            return true;
        }

        PlayerProfile profile = plugin.getEconomyService().getOrLoadProfile(player.getUniqueId(), player.getName());
        player.sendMessage(plugin.getConfigManager().msg("economy.balance", Map.of(
                "credits", TextUtil.formatCredits(profile.getCredits()),
                "potatoes", TextUtil.formatCredits(profile.getLifetimePotatoes())
        )));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission("potatosmp.balance.others")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
