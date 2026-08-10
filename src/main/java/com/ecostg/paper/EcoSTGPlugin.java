package com.ecostg.paper;

import com.ecostg.paper.command.EcoCommands;
import com.ecostg.paper.economy.AuctionService;
import com.ecostg.paper.economy.Database;
import com.ecostg.paper.economy.EconomyService;
import com.ecostg.paper.economy.WorthService;
import com.ecostg.paper.gui.GuiListener;
import com.ecostg.paper.gui.GuiManager;
import com.ecostg.paper.job.JobService;
import com.ecostg.paper.listener.ChatInputListener;
import com.ecostg.paper.listener.PlayerListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class EcoSTGPlugin extends JavaPlugin {

    private Database database;
    private EconomyService economy;
    private AuctionService auctions;
    private WorthService worth;
    private JobService jobs;
    private GuiManager guis;
    private ChatInputListener chatInput;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("worth.yml", false);
        saveResource("jobs.yml", false);

        database = new Database(this);
        database.connect();

        economy = new EconomyService(this, database);
        auctions = new AuctionService(this, database, economy);
        worth = new WorthService(this);
        jobs = new JobService(this, database, economy);
        guis = new GuiManager(this);
        chatInput = new ChatInputListener(this);

        EcoCommands commands = new EcoCommands(this);
        for (String name : new String[]{
                "pay", "shop", "sell", "ecostg", "moneytop", "job", "jobsell", "job-timer-reset"
        }) {
            var cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(commands);
                cmd.setTabCompleter(commands);
            }
        }

        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(chatInput, this);

        jobs.startScheduler();
        getLogger().info("EcoSTG Paper enabled.");
    }

    @Override
    public void onDisable() {
        if (jobs != null) {
            jobs.stopScheduler();
        }
        if (database != null) {
            database.close();
        }
        getLogger().info("EcoSTG Paper disabled.");
    }

    public void logAction(String message) {
        getLogger().info("[ACTION] " + message);
    }

    public EconomyService economy() {
        return economy;
    }

    public AuctionService auctions() {
        return auctions;
    }

    public WorthService worth() {
        return worth;
    }

    public JobService jobs() {
        return jobs;
    }

    public GuiManager guis() {
        return guis;
    }

    public ChatInputListener chatInput() {
        return chatInput;
    }

    public Database database() {
        return database;
    }
}
