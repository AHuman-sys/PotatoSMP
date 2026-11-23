package com.potatosmp.commands;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.database.PlayerProfile;
import com.potatosmp.database.TransactionType;
import com.potatosmp.items.ItemFactory;
import com.potatosmp.items.PotatoItemType;
import com.potatosmp.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final PotatoSMPPlugin plugin;

    public AdminCommand(PotatoSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("potatosmp.admin")) {
            sender.sendMessage(plugin.getConfigManager().msg("general.no-permission"));
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give" -> handleGive(sender, args);
            case "credits" -> handleCredits(sender, args);
            case "reload" -> handleReload(sender);
            case "setbank" -> handleSetBank(sender);
            case "setbills" -> handleSetBills(sender);
            case "reset" -> handleReset(sender, args);
            case "monument" -> handleMonument(sender);
            case "competition" -> handleCompetition(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextUtil.mm("<gold>Usage:</gold> <yellow>/potatoadmin give <player> <item> [amount]</yellow>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getConfigManager().msg("general.player-not-found", Map.of("player", args[1])));
            return;
        }
        PotatoItemType type = PotatoItemType.fromId(args[2]);
        if (type == null) {
            try {
                type = PotatoItemType.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException ex) {
                sender.sendMessage(TextUtil.mm("<red>Unknown PotatoSMP item:</red> <yellow>" + args[2] + "</yellow>"));
                return;
            }
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException ex) {
                sender.sendMessage(plugin.getConfigManager().msg("general.invalid-amount"));
                return;
            }
        }
        for (int i = 0; i < (type.isUniqueToken() ? amount : 1); i++) {
            ItemStack item = ItemFactory.create(type, type.isUniqueToken() ? 1 : amount);
            var left = target.getInventory().addItem(item);
            left.values().forEach(d -> target.getWorld().dropItemNaturally(target.getLocation(), d));
        }
        sender.sendMessage(TextUtil.mm("<green>Gave <yellow>" + amount + "x " + type.getId()
                + "</yellow> to <gold>" + target.getName() + "</gold>.</green>"));
    }

    private void handleCredits(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(TextUtil.mm("<gold>Usage:</gold> <yellow>/potatoadmin credits <give|take|set> <player> <amount></yellow>"));
            return;
        }
        String action = args[1].toLowerCase();
        Optional<PlayerProfile> targetOpt = plugin.getEconomyService().findByName(args[2]);
        Player online = Bukkit.getPlayerExact(args[2]);
        if (targetOpt.isEmpty() && online == null) {
            sender.sendMessage(plugin.getConfigManager().msg("general.player-not-found", Map.of("player", args[2])));
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[3]);
            if (amount < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            sender.sendMessage(plugin.getConfigManager().msg("general.invalid-amount"));
            return;
        }

        var uuid = online != null ? online.getUniqueId() : targetOpt.get().getUuid();
        var name = online != null ? online.getName() : targetOpt.get().getLastKnownName();

        boolean ok = switch (action) {
            case "give", "add" -> plugin.getEconomyService().addCredits(uuid, name, amount,
                    TransactionType.ADMIN_ADJUST, "Admin grant by " + sender.getName());
            case "take", "remove" -> plugin.getEconomyService().withdrawCredits(uuid, name, amount,
                    TransactionType.ADMIN_ADJUST, "Admin deduction by " + sender.getName());
            case "set" -> plugin.getEconomyService().setCredits(uuid, name, amount,
                    "Admin set by " + sender.getName());
            default -> false;
        };
        if (ok) {
            long newBal = plugin.getEconomyService().getBalance(uuid);
            sender.sendMessage(TextUtil.mm("<green>Updated <gold>" + name + "</gold>'s credits to <yellow>"
                    + TextUtil.formatCredits(newBal) + "</yellow>.</green>"));
            if (online != null) {
                plugin.getScoreboardService().updatePlayer(online);
            }
        } else {
            sender.sendMessage(TextUtil.mm("<red>Could not update credits (check action or sufficient balance).</red>"));
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadPluginState();
        sender.sendMessage(plugin.getConfigManager().msg("general.reloaded"));
    }

    private void handleSetBank(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().msg("general.player-only"));
            return;
        }
        plugin.getConfigManager().setLocation("bank.bank-location", player.getLocation());
        plugin.getConfigManager().getConfig().set("bank.bank-location.configured", true);
        plugin.getConfigManager().saveMainConfig();
        sender.sendMessage(TextUtil.mm("<green>Potato Bank location set to your current coordinates!</green>"));
    }

    private void handleSetBills(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().msg("general.player-only"));
            return;
        }
        plugin.getPotatoBillManager().spawnOrRelocatePotatoBill(player.getLocation());
        sender.sendMessage(TextUtil.mm("<green>Spawned <gold>Potato Bill</gold> NPC at your location!</green>"));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.mm("<gold>Usage:</gold> <yellow>/potatoadmin reset <player></yellow>"));
            return;
        }
        Optional<PlayerProfile> targetOpt = plugin.getEconomyService().findByName(args[1]);
        Player online = Bukkit.getPlayerExact(args[1]);
        if (targetOpt.isEmpty() && online == null) {
            sender.sendMessage(plugin.getConfigManager().msg("general.player-not-found", Map.of("player", args[1])));
            return;
        }
        var uuid = online != null ? online.getUniqueId() : targetOpt.get().getUuid();
        var name = online != null ? online.getName() : targetOpt.get().getLastKnownName();

        try {
            plugin.getDatabaseManager().resetPlayerData(uuid);
            PlayerProfile profile = plugin.getEconomyService().getOrLoadProfile(uuid, name);
            profile.setCredits(0);
            profile.setLifetimePotatoes(0);
            profile.setPotatoHeartsUsed(0);
            if (online != null) {
                plugin.getCustomEquipmentListener().applyPotatoHeartAttribute(online);
                plugin.getScoreboardService().updatePlayer(online);
            }
            sender.sendMessage(TextUtil.mm("<green>Reset PotatoSMP economy & heart data for <gold>" + name + "</gold>.</green>"));
        } catch (SQLException e) {
            sender.sendMessage(TextUtil.mm("<red>Error resetting player data: " + e.getMessage() + "</red>"));
        }
    }

    private void handleMonument(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().msg("general.player-only"));
            return;
        }
        plugin.getMonumentService().setMonumentLocation(player.getLocation());
        sender.sendMessage(TextUtil.mm("<green>Community Potato Monument placed & hologram refreshed at your location!</green>"));
    }

    private void handleCompetition(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("rollover")) {
            plugin.getCompetitionService().finalizeAndStartNextWeek();
            sender.sendMessage(TextUtil.mm("<green>Finalized current weekly competition and started a fresh week!</green>"));
            return;
        }
        var week = plugin.getCompetitionService().getCurrentWeek();
        if (week == null) {
            sender.sendMessage(TextUtil.mm("<yellow>Weekly competition is currently disabled or unavailable.</yellow>"));
            return;
        }
        long rem = (week.endsAt() - System.currentTimeMillis()) / 1000L;
        sender.sendMessage(TextUtil.mm("<gold>Week #" + week.weekId() + "</gold> <yellow>Ends in: "
                + TextUtil.formatDurationSeconds(rem) + "</yellow>"));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(TextUtil.mm("<gold><bold>PotatoSMP Admin Commands:</bold></gold>"));
        sender.sendMessage(TextUtil.mm("<yellow>/potatoadmin give <player> <item> [amount]</yellow>"));
        sender.sendMessage(TextUtil.mm("<yellow>/potatoadmin credits <give|take|set> <player> <amount></yellow>"));
        sender.sendMessage(TextUtil.mm("<yellow>/potatoadmin setbank | setbills | monument</yellow>"));
        sender.sendMessage(TextUtil.mm("<yellow>/potatoadmin competition <info|rollover></yellow>"));
        sender.sendMessage(TextUtil.mm("<yellow>/potatoadmin reset <player> | reload</yellow>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("potatosmp.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("give", "credits", "reload", "setbank", "setbills", "reset", "monument", "competition");
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "give", "reset" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                case "credits" -> List.of("give", "take", "set");
                case "competition" -> List.of("info", "rollover");
                default -> List.of();
            };
        }
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give")) {
                return Arrays.stream(PotatoItemType.values()).map(PotatoItemType::getId).toList();
            }
            if (args[0].equalsIgnoreCase("credits")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            }
        }
        return List.of();
    }
}
