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
import com.ecostg.paper.listener.MenuFeatureListener;
import com.ecostg.paper.menu.FriendService;
import com.ecostg.paper.menu.HomeService;
import com.ecostg.paper.menu.MenuHub;
import com.ecostg.paper.menu.RtpService;
import com.ecostg.paper.menu.SettingsService;
import com.ecostg.paper.menu.StatsService;
import com.ecostg.paper.menu.TpaService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.List;

public final class EcoSTGPlugin extends JavaPlugin {

    private Database database;
    private EconomyService economy;
    private AuctionService auctions;
    private WorthService worth;
    private JobService jobs;
    private GuiManager guis;
    private ChatInputListener chatInput;
    private MenuHub menus;
    private HomeService homes;
    private FriendService friends;
    private StatsService stats;
    private SettingsService settings;
    private TpaService tpa;
    private RtpService rtp;
    private BukkitTask playtimeTask;

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
        homes = new HomeService(this, database);
        friends = new FriendService(this, database);
        stats = new StatsService(this, database);
        settings = new SettingsService(this, database);
        tpa = new TpaService(this);
        rtp = new RtpService(this);
        menus = new MenuHub(this);
        menus.registerServerLink();

        EcoCommands commands = new EcoCommands(this);
        registerPaperCommand("pay", "Pay a player", List.of(), commands);
        registerPaperCommand("shop", "Open the auction house", List.of("ah", "auction"), commands);
        registerPaperCommand("sell", "List an item on the auction house", List.of(), commands);
        registerPaperCommand("menu", "Open the EcoSTG hub menu", List.of("ecostgmenu"), commands);
        registerPaperCommand("tpa", "Request to teleport to a player", List.of(), commands);
        registerPaperCommand("tpahere", "Request a player to teleport to you", List.of("tphere"), commands);
        registerPaperCommand("tpaccept", "Accept a teleport request", List.of("tpyes"), commands);
        registerPaperCommand("tpdeny", "Deny a teleport request", List.of("tpno"), commands);
        registerPaperCommand("rtp", "Random teleport in this world", List.of(), commands);
        registerPaperCommand("rtpq", "Teleport to a random online player", List.of("rtpqueue"), commands);
        registerPaperCommand("stats", "View player stats", List.of(), commands);
        registerPaperCommand("home", "Open homes or teleport to a home", List.of("homes"), commands);
        registerPaperCommand("sethome", "Set a home at your location", List.of(), commands);
        registerPaperCommand("delhome", "Delete a home", List.of(), commands);
        registerPaperCommand("friend", "Follow players; friends when both follow", List.of("friends"), commands);
        registerPaperCommand("settings", "Open EcoSTG settings", List.of(), commands);
        registerPaperCommand("leaderboard", "View leaderboards", List.of("leaderboards", "lb"), commands);
        registerPaperCommand("bal", "Show a player's balance", List.of("balance", "money"), commands);
        registerPaperCommand("ecostg", "EcoSTG help and admin commands", List.of(), commands);
        registerPaperCommand("moneytop", "View the richest players", List.of(), commands);
        registerPaperCommand("job", "Open the jobs GUI", List.of(), commands);
        registerPaperCommand("jobsell", "Sell required job items on the AH", List.of(), commands);
        registerPaperCommand("jobinfo", "Show your current job status", List.of(), commands);
        registerPaperCommand("job-timer-reset", "Reset a fired player's job cooldown", List.of(), "ecostg.admin", commands);
        registerPaperCommand("ecoset", "Set a player's balance", List.of(), "ecostg.admin", commands);
        registerPaperCommand("ecogive", "Give money to a player", List.of(), "ecostg.admin", commands);
        registerPaperCommand("jobcancel", "Cancel a player's active job", List.of(), "ecostg.admin", commands);
        registerPaperCommand("activejobs", "View all active jobs", List.of(), "ecostg.admin", commands);

        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuFeatureListener(this), this);
        getServer().getPluginManager().registerEvents(chatInput, this);

        jobs.startScheduler();
        playtimeTask = getServer().getScheduler().runTaskTimer(this, () -> stats.flushAllOnline(), 20L * 60, 20L * 60);
        getLogger().info("EcoSTG Paper enabled.");
    }

    @Override
    public void onDisable() {
        if (playtimeTask != null) {
            playtimeTask.cancel();
        }
        if (stats != null) {
            stats.flushAllOnline();
        }
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

    public MenuHub menus() {
        return menus;
    }

    public HomeService homes() {
        return homes;
    }

    public FriendService friends() {
        return friends;
    }

    public StatsService stats() {
        return stats;
    }

    public SettingsService settings() {
        return settings;
    }

    public TpaService tpa() {
        return tpa;
    }

    public RtpService rtp() {
        return rtp;
    }

    private void registerPaperCommand(String label, String description, List<String> aliases, EcoCommands commands) {
        registerPaperCommand(label, description, aliases, null, commands);
    }

    private void registerPaperCommand(String label, String description, List<String> aliases,
                                      String permission, EcoCommands commands) {
        BasicCommand basic = new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                commands.execute(source.getSender(), label, args);
            }

            @Override
            public Collection<String> suggest(CommandSourceStack source, String[] args) {
                return commands.suggest(source.getSender(), label, args);
            }

            @Override
            public String permission() {
                return permission;
            }

            @Override
            public boolean canUse(CommandSender sender) {
                return permission == null || sender.hasPermission(permission);
            }
        };
        registerCommand(label, description, aliases, basic);
    }
}
