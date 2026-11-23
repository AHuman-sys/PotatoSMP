package com.potatosmp.commands;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.database.PlayerProfile;
import com.potatosmp.util.TextUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;

public final class ScoreboardCommand implements CommandExecutor {

    private final PotatoSMPPlugin plugin;

    public ScoreboardCommand(PotatoSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().msg("general.player-only"));
            return true;
        }
        if (!player.hasPermission("potatosmp.scoreboard")) {
            player.sendMessage(plugin.getConfigManager().msg("general.no-permission"));
            return true;
        }
        PlayerProfile profile = plugin.getEconomyService().getOrLoadProfile(player.getUniqueId(), player.getName());
        boolean next = !profile.isScoreboardEnabled();
        profile.setScoreboardEnabled(next);
        try {
            plugin.getDatabaseManager().savePlayer(profile);
        } catch (SQLException ignored) {}
        plugin.getScoreboardService().updatePlayer(player);
        player.sendMessage(TextUtil.mm(next
                ? "<green>PotatoSMP scoreboard enabled.</green>"
                : "<yellow>PotatoSMP scoreboard hidden.</yellow>"));
        return true;
    }
}
