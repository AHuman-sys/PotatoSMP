package com.potatosmp;

import com.potatosmp.bounty.BountyListener;
import com.potatosmp.bounty.BountyService;
import com.potatosmp.commands.AdminCommand;
import com.potatosmp.commands.BalanceCommand;
import com.potatosmp.commands.BankCommand;
import com.potatosmp.commands.BountyCommand;
import com.potatosmp.commands.ScoreboardCommand;
import com.potatosmp.commands.ShopCommand;
import com.potatosmp.commands.TopCommand;
import com.potatosmp.commands.TradeCommand;
import com.potatosmp.config.ConfigManager;
import com.potatosmp.database.DatabaseManager;
import com.potatosmp.economy.EconomyService;
import com.potatosmp.economy.MarketService;
import com.potatosmp.economy.PotatoBillManager;
import com.potatosmp.features.CompetitionService;
import com.potatosmp.features.MonumentService;
import com.potatosmp.features.ScoreboardService;
import com.potatosmp.gui.MenuListener;
import com.potatosmp.items.CustomEquipmentListener;
import com.potatosmp.items.GoldenPotatoRecipeManager;
import com.potatosmp.items.ItemFactory;
import com.potatosmp.items.ItemIntegrityListener;
import com.potatosmp.netherite.NetheriteRestrictionListener;
import com.potatosmp.trade.TradeService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;

public final class PotatoSMPPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private MarketService marketService;
    private EconomyService economyService;
    private PotatoBillManager potatoBillManager;
    private CustomEquipmentListener customEquipmentListener;
    private GoldenPotatoRecipeManager recipeManager;
    private NetheriteRestrictionListener netheriteRestrictionListener;
    private BountyService bountyService;
    private TradeService tradeService;
    private CompetitionService competitionService;
    private MonumentService monumentService;
    private ScoreboardService scoreboardService;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.loadAll();

        ItemFactory.init(this);

        this.databaseManager = new DatabaseManager(new File(getDataFolder(), "potatosmp.db"));
        try {
            this.databaseManager.init();
        } catch (SQLException e) {
            getLogger().severe("Fatal SQLite initialization failure: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.marketService = new MarketService(this, configManager);
        this.economyService = new EconomyService(this, configManager, databaseManager, marketService);
        this.potatoBillManager = new PotatoBillManager(this, configManager);
        this.customEquipmentListener = new CustomEquipmentListener(this, configManager, economyService);
        this.recipeManager = new GoldenPotatoRecipeManager(this, configManager);
        this.netheriteRestrictionListener = new NetheriteRestrictionListener(this, configManager);
        this.bountyService = new BountyService(this, configManager, databaseManager, economyService);
        this.tradeService = new TradeService(this, configManager, databaseManager, economyService);
        this.competitionService = new CompetitionService(this, configManager, databaseManager, economyService);
        this.monumentService = new MonumentService(this, configManager, databaseManager, economyService);
        this.scoreboardService = new ScoreboardService(this, configManager, economyService,
                marketService, competitionService, monumentService);

        registerListeners();
        registerCommands();

        this.recipeManager.registerRecipes();
        this.netheriteRestrictionListener.applyRecipeRestrictions();
        this.marketService.start();
        this.customEquipmentListener.start();
        this.bountyService.start();
        this.competitionService.start();
        this.scoreboardService.start();

        Bukkit.getScheduler().runTaskLater(this, () -> {
            potatoBillManager.ensureSpawnedIfConfigured();
            monumentService.updateHologram();
            for (Player player : Bukkit.getOnlinePlayers()) {
                economyService.getOrLoadProfile(player.getUniqueId(), player.getName());
                customEquipmentListener.applyPotatoHeartAttribute(player);
                customEquipmentListener.syncSpudSeeker(player, false);
            }
        }, 20L);

        getLogger().info("PotatoSMP enabled cleanly on Paper " + getServer().getMinecraftVersion() + "!");
    }

    @Override
    public void onDisable() {
        if (scoreboardService != null) scoreboardService.stop();
        if (competitionService != null) competitionService.stop();
        if (bountyService != null) bountyService.stop();
        if (customEquipmentListener != null) customEquipmentListener.stop();
        if (marketService != null) marketService.stop();
        if (recipeManager != null) recipeManager.unregisterRecipes();
        if (economyService != null) economyService.flushAll();
        if (databaseManager != null) databaseManager.close();
        getLogger().info("PotatoSMP disabled and all balances flushed safely.");
    }

    public void reloadPluginState() {
        configManager.loadAll();
        marketService.start();
        recipeManager.registerRecipes();
        netheriteRestrictionListener.applyRecipeRestrictions();
        customEquipmentListener.configureWorldLocatorBars();
        competitionService.start();
        scoreboardService.start();
        monumentService.updateHologram();
        for (Player player : Bukkit.getOnlinePlayers()) {
            customEquipmentListener.applyPotatoHeartAttribute(player);
            customEquipmentListener.syncSpudSeeker(player, false);
            scoreboardService.updatePlayer(player);
        }
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(potatoBillManager, this);
        pm.registerEvents(new ItemIntegrityListener(this, configManager), this);
        pm.registerEvents(customEquipmentListener, this);
        pm.registerEvents(netheriteRestrictionListener, this);
        pm.registerEvents(new BountyListener(bountyService), this);
    }

    private void registerCommands() {
        ShopCommand shopCmd = new ShopCommand(this);
        bindCommand("shop", shopCmd, shopCmd);

        BountyCommand bountyCmd = new BountyCommand(this);
        bindCommand("bounty", bountyCmd, bountyCmd);

        bindCommand("bank", new BankCommand(this), null);

        BalanceCommand balCmd = new BalanceCommand(this);
        bindCommand("potatobalance", balCmd, balCmd);

        TopCommand topCmd = new TopCommand(this);
        bindCommand("potatotop", topCmd, topCmd);

        TradeCommand tradeCmd = new TradeCommand(this);
        bindCommand("trade", tradeCmd, tradeCmd);

        bindCommand("potatoscoreboard", new ScoreboardCommand(this), null);

        AdminCommand adminCmd = new AdminCommand(this);
        bindCommand("potatoadmin", adminCmd, adminCmd);
    }

    private void bindCommand(String name, org.bukkit.command.CommandExecutor executor,
                             org.bukkit.command.TabCompleter completer) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            if (completer != null) {
                cmd.setTabCompleter(completer);
            }
        }
    }

    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public MarketService getMarketService() { return marketService; }
    public EconomyService getEconomyService() { return economyService; }
    public PotatoBillManager getPotatoBillManager() { return potatoBillManager; }
    public CustomEquipmentListener getCustomEquipmentListener() { return customEquipmentListener; }
    public BountyService getBountyService() { return bountyService; }
    public TradeService getTradeService() { return tradeService; }
    public CompetitionService getCompetitionService() { return competitionService; }
    public MonumentService getMonumentService() { return monumentService; }
    public ScoreboardService getScoreboardService() { return scoreboardService; }
}
