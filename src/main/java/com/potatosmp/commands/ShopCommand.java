package com.potatosmp.commands;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.gui.ShopCategoryMenu;
import com.potatosmp.gui.ShopItemsMenu;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class ShopCommand implements CommandExecutor, TabCompleter {

    private final PotatoSMPPlugin plugin;

    public ShopCommand(PotatoSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().msg("general.player-only"));
            return true;
        }
        if (!player.hasPermission("potatosmp.shop")) {
            player.sendMessage(plugin.getConfigManager().msg("general.no-permission"));
            return true;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.15f);
        if (args.length > 0) {
            String cat = args[0].toLowerCase();
            if (plugin.getConfigManager().getShopConfig().isConfigurationSection("categories." + cat)) {
                new ShopItemsMenu(plugin, player, cat).open();
                return true;
            }
        }
        new ShopCategoryMenu(plugin, player).open();
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            var sec = plugin.getConfigManager().getShopConfig().getConfigurationSection("categories");
            if (sec != null) {
                return new ArrayList<>(sec.getKeys(false));
            }
        }
        return List.of();
    }
}
