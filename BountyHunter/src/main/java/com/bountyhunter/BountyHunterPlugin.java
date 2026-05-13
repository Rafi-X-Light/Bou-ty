package com.bountyhunter;

import org.bukkit.plugin.java.JavaPlugin;

public class BountyHunterPlugin extends JavaPlugin {
    private EconomyHandler economyHandler;
    private BountyManager bountyManager;
    private MessageUtil messageUtil;
    private CrossplayUtil crossplayUtil;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.messageUtil = new MessageUtil();
        this.crossplayUtil = new CrossplayUtil();
        this.economyHandler = new EconomyHandler();

        if (!economyHandler.setupEconomy()) {
            getLogger().severe("BountyHunter requires Vault and a compatible economy plugin. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.bountyManager = new BountyManager(this);

        BountyCommand bountyCommand = new BountyCommand(this);
        getCommand("bounty").setExecutor(bountyCommand);
        getCommand("bounty").setTabCompleter(bountyCommand);

        getServer().getPluginManager().registerEvents(new BountyListener(this), this);

        getLogger().info("BountyHunter 2.0.0 enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (bountyManager != null) {
            bountyManager.saveData();
        }
        getLogger().info("BountyHunter 2.0.0 disabled.");
    }

    public EconomyHandler getEconomyHandler() { return economyHandler; }
    public BountyManager getBountyManager() { return bountyManager; }
    public MessageUtil getMessageUtil() { return messageUtil; }
    public CrossplayUtil getCrossplayUtil() { return crossplayUtil; }
}
