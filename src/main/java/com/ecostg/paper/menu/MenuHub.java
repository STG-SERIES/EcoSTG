package com.ecostg.paper.menu;

import com.ecostg.paper.EcoSTGPlugin;
import com.ecostg.paper.job.JobDefinition;
import com.ecostg.paper.job.JobService;
import com.ecostg.paper.util.Messages;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.ServerLinks;
import org.bukkit.entity.Player;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class MenuHub {

    public static final String OPEN_MAIN_KEY = "ecostg:open_main";
    public static final Key HUB_HOMES = Key.key("ecostg:hub_homes");
    public static final Key HUB_AUCTION = Key.key("ecostg:hub_auction");
    public static final Key HUB_SELL = Key.key("ecostg:hub_sell");
    public static final Key HUB_TELEPORT = Key.key("ecostg:hub_teleport");
    public static final Key HUB_LEADERBOARDS = Key.key("ecostg:hub_leaderboards");
    public static final Key HUB_RTP = Key.key("ecostg:hub_rtp");
    public static final Key HUB_RTP_QUEUE = Key.key("ecostg:hub_rtp_queue");
    public static final Key HUB_FRIENDS = Key.key("ecostg:hub_friends");
    public static final Key HUB_PAY = Key.key("ecostg:hub_pay");
    public static final Key HUB_STATS = Key.key("ecostg:hub_stats");
    public static final Key HUB_SETTINGS = Key.key("ecostg:hub_settings");
    public static final Key HUB_CLOSE = Key.key("ecostg:hub_close");

    private static final double[] PAY_PRESETS = {1000, 3400, 10000, 17000, 34000, 100000};

    private final EcoSTGPlugin plugin;
    private ServerLinks.ServerLink menuLink;

    public MenuHub(EcoSTGPlugin plugin) {
        this.plugin = plugin;
    }

    public String mainLetters() {
        return plugin.getConfig().getString("menu.main-letters", "EcoSTG");
    }

    public void setMainLetters(String name) {
        plugin.getConfig().set("menu.main-letters", name);
        plugin.saveConfig();
        refreshServerLink();
        plugin.logAction("Main letters changed to '" + name + "'");
    }

    public void registerServerLink() {
        refreshServerLink();
    }

    public void refreshServerLink() {
        ServerLinks links = Bukkit.getServer().getServerLinks();
        if (menuLink != null) {
            links.removeLink(menuLink);
            menuLink = null;
        }
        try {
            menuLink = links.addLink(Component.text(mainLetters()), URI.create("https://ecostg.menu/"));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not register Server Links entry: " + e.getMessage());
        }
    }

    public void openMain(Player player) {
        List<ActionButton> actions = new ArrayList<>();
        actions.add(btn("Homes", "Set & teleport to homes", p -> openHomes(p)));
        actions.add(btn("Auction", "Browse the auction house", this::openAuctionSafe));
        actions.add(btn("Sell", "List an item for sale", this::openSellSafe));
        actions.add(btn("Teleport", "TPA / TPAHere", this::openTeleport));
        actions.add(btn("Leaderboards", "Top players", this::openLeaderboards));
        actions.add(btn("RTP", "Random teleport (" + plugin.rtp().cooldownSeconds() + "s cooldown)", p -> {
            if (plugin.rtp().rtp(p)) {
                p.closeDialog();
            } else {
                openMain(p);
            }
        }));
        actions.add(btn("RTP Queue", "Teleport to a random player", p -> {
            if (plugin.rtp().rtpQueue(p)) {
                p.closeDialog();
            } else {
                openMain(p);
            }
        }));
        actions.add(btn("Friends", "Follow players; friends when both follow", this::openFriends));
        actions.add(btn("Pay", "Send money", this::openPayPlayers));
        actions.add(btn("Stats", "View player stats", this::openStatsPlayers));
        actions.add(btn("Settings", "Privacy, chat, visuals", this::openSettings));

        show(player, mainLetters(), "Server hub menu", actions, 2, null);
    }

    /**
     * Handles pause-screen hub buttons (registry dialog custom clicks).
     */
    public void handleRegistryHubAction(Player player, Key key) {
        if (key.equals(HUB_HOMES)) {
            openHomes(player);
        } else if (key.equals(HUB_AUCTION)) {
            openAuctionSafe(player);
        } else if (key.equals(HUB_SELL)) {
            openSellSafe(player);
        } else if (key.equals(HUB_TELEPORT)) {
            openTeleport(player);
        } else if (key.equals(HUB_LEADERBOARDS)) {
            openLeaderboards(player);
        } else if (key.equals(HUB_RTP)) {
            if (plugin.rtp().rtp(player)) {
                player.closeDialog();
            } else {
                openMain(player);
            }
        } else if (key.equals(HUB_RTP_QUEUE)) {
            if (plugin.rtp().rtpQueue(player)) {
                player.closeDialog();
            } else {
                openMain(player);
            }
        } else if (key.equals(HUB_FRIENDS)) {
            openFriends(player);
        } else if (key.equals(HUB_PAY)) {
            openPayPlayers(player);
        } else if (key.equals(HUB_STATS)) {
            openStatsPlayers(player);
        } else if (key.equals(HUB_SETTINGS)) {
            openSettings(player);
        } else if (key.equals(HUB_CLOSE)) {
            player.closeDialog();
        } else {
            openMain(player);
        }
    }

    // --- Homes ---

    public void openHomes(Player player) {
        List<HomeService.Home> homes = plugin.homes().list(player.getUniqueId());
        List<ActionButton> actions = new ArrayList<>();
        for (HomeService.Home home : homes) {
            String name = home.name();
            actions.add(btn("Go: " + name, "Teleport instantly", p -> {
                if (plugin.homes().teleport(p, name)) {
                    Messages.send(plugin, p, "<green>Teleported to home '" + name + "'.</green>");
                }
            }));
            actions.add(btn("Del: " + name, "Delete this home", p -> {
                plugin.homes().deleteHome(p.getUniqueId(), name);
                Messages.send(plugin, p, "<yellow>Deleted home '" + name + "'.</yellow>");
                openHomes(p);
            }));
        }
        int max = plugin.homes().maxHomes();
        if (homes.size() < max) {
            String next = "home" + (homes.size() + 1);
            actions.add(btn("Set " + next, "Save your current location", p -> {
                if (plugin.homes().setHome(p, next)) {
                    Messages.send(plugin, p, "<green>Home '" + next + "' set.</green>");
                } else {
                    Messages.send(plugin, p, "<red>Could not set home (max " + max + ").</red>");
                }
                openHomes(p);
            }));
        }
        show(player, "Homes", "Max " + max + " homes. Teleport is instant.", actions, 2, this::openMain);
    }

    // --- Auction / Sell ---

    private void openAuctionSafe(Player player) {
        if (!plugin.settings().get(player.getUniqueId()).auctionEnabled()) {
            Messages.send(plugin, player, "<red>You disabled Auction/Sell in Settings.</red>");
            openMain(player);
            return;
        }
        player.closeDialog();
        Bukkit.getScheduler().runTask(plugin, () -> plugin.guis().openAuction(player, 0));
    }

    private void openSellSafe(Player player) {
        if (!plugin.settings().get(player.getUniqueId()).auctionEnabled()) {
            Messages.send(plugin, player, "<red>You disabled Auction/Sell in Settings.</red>");
            openMain(player);
            return;
        }
        player.closeDialog();
        Bukkit.getScheduler().runTask(plugin, () -> plugin.guis().openSell(player));
    }

    // --- Teleport ---

    public void openTeleport(Player player) {
        List<ActionButton> actions = List.of(
                btn("TPA (you → them)", "Ask to teleport to a player", p -> openTeleportTargets(p, TpaService.Type.TPA)),
                btn("TPAHere (them → you)", "Ask a player to teleport to you", p -> openTeleportTargets(p, TpaService.Type.TPA_HERE))
        );
        show(player, "Teleport", "Target must accept unless privacy allows instant.", actions, 1, this::openMain);
    }

    public void openTeleportTargets(Player player, TpaService.Type type) {
        List<ActionButton> actions = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            String name = online.getName();
            actions.add(btn(name, type == TpaService.Type.TPA ? "Request TPA" : "Request TPAHere", p -> {
                Player target = Bukkit.getPlayerExact(name);
                if (target == null) {
                    Messages.send(plugin, p, "<red>Player offline.</red>");
                    openTeleport(p);
                    return;
                }
                plugin.tpa().request(p, target, type);
                p.closeDialog();
            }));
        }
        if (actions.isEmpty()) {
            actions.add(btn("No players online", "Try again later", this::openTeleport));
        }
        show(player, type == TpaService.Type.TPA ? "TPA Target" : "TPAHere Target",
                "Pick a player", actions, 2, this::openTeleport);
    }

    public void openTpaPrompt(Player target, Player from, TpaService.Type type) {
        String label = type == TpaService.Type.TPA ? "TPA" : "TPAHere";
        List<ActionButton> actions = List.of(
                btn("Accept", "Accept " + label + " from " + from.getName(), p -> {
                    plugin.tpa().accept(p);
                    p.closeDialog();
                }),
                btn("Deny", "Deny request", p -> {
                    plugin.tpa().deny(p);
                    p.closeDialog();
                })
        );
        show(target, "Teleport Request", from.getName() + " requested " + label + ".", actions, 2, null);
    }

    // --- Leaderboards ---

    public void openLeaderboards(Player player) {
        List<ActionButton> actions = new ArrayList<>();
        for (StatsService.LeaderboardType type : StatsService.LeaderboardType.values()) {
            actions.add(btn(prettyBoard(type), "Top 10", p -> openLeaderboard(p, type)));
        }
        show(player, "Leaderboards", "Choose a category", actions, 2, this::openMain);
    }

    public void openLeaderboard(Player player, StatsService.LeaderboardType type) {
        List<StatsService.LeaderboardEntry> top = plugin.stats().top(type, 10);
        StringBuilder body = new StringBuilder();
        int i = 1;
        for (StatsService.LeaderboardEntry entry : top) {
            body.append("#").append(i++).append(" ").append(entry.name()).append(" — ")
                    .append(formatBoardValue(type, entry.value())).append("\n");
        }
        if (top.isEmpty()) {
            body.append("No data yet.");
        }
        List<ActionButton> actions = List.of(btn("Back", "Return", this::openLeaderboards));
        showNotice(player, prettyBoard(type), body.toString().trim(), actions);
    }

    // --- Friends ---

    public void openFriends(Player player) {
        UUID id = player.getUniqueId();
        List<ActionButton> actions = new ArrayList<>();
        actions.add(btn("Follow", "Follow an online player", this::openFriendsAdd));
        actions.add(btn("Unfollow", "Stop following someone", this::openFriendsRemove));
        if (!plugin.friends().listPendingFollowers(id).isEmpty()) {
            actions.add(btn("Follow back", "People following you", this::openFriendsFollowBack));
        }
        show(player, "Friends", friendsBody(id), actions, 2, this::openMain);
    }

    private String friendsBody(UUID id) {
        String friends = joinNames(plugin.friends().listFriends(id));
        String following = joinNames(plugin.friends().listPendingFollowing(id));
        String followers = joinNames(plugin.friends().listPendingFollowers(id));
        return "Friends (both follow): " + friends
                + "\nFollowing: " + following
                + "\nFollowers: " + followers;
    }

    private static String joinNames(List<FriendService.FriendEntry> entries) {
        if (entries.isEmpty()) {
            return "none";
        }
        return String.join(", ", entries.stream().map(FriendService.FriendEntry::name).toList());
    }

    private void openFriendsAdd(Player player) {
        List<ActionButton> actions = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (plugin.friends().isFollowing(player.getUniqueId(), online.getUniqueId())) {
                continue;
            }
            String name = online.getName();
            UUID id = online.getUniqueId();
            boolean theyFollow = plugin.friends().isFollowing(id, player.getUniqueId());
            actions.add(btn(name, theyFollow ? "Follow back to become friends" : "Follow this player", p -> {
                plugin.economy().ensurePlayer(online);
                plugin.friends().follow(p.getUniqueId(), id);
                openFriends(p);
            }));
        }
        if (actions.isEmpty()) {
            actions.add(btn("Nobody to follow", "You already follow every online player", this::openFriends));
        }
        show(player, "Follow", "Online players you don't follow yet", actions, 2, this::openFriends);
    }

    private void openFriendsRemove(Player player) {
        List<ActionButton> actions = new ArrayList<>();
        for (FriendService.FriendEntry entry : plugin.friends().listFollowing(player.getUniqueId())) {
            UUID id = entry.uuid();
            String name = entry.name();
            boolean friends = plugin.friends().areFriends(player.getUniqueId(), id);
            actions.add(btn(name, friends ? "Unfollow (ends friendship)" : "Unfollow", p -> {
                plugin.friends().unfollow(p.getUniqueId(), id);
                openFriends(p);
            }));
        }
        if (actions.isEmpty()) {
            actions.add(btn("Not following anyone", "Your following list is empty", this::openFriends));
        }
        show(player, "Unfollow", "People you follow", actions, 2, this::openFriends);
    }

    private void openFriendsFollowBack(Player player) {
        List<ActionButton> actions = new ArrayList<>();
        for (FriendService.FriendEntry entry : plugin.friends().listPendingFollowers(player.getUniqueId())) {
            UUID id = entry.uuid();
            String name = entry.name();
            actions.add(btn(name, "Follow back to become friends", p -> {
                plugin.friends().follow(p.getUniqueId(), id);
                openFriends(p);
            }));
        }
        if (actions.isEmpty()) {
            actions.add(btn("No pending followers", "Nobody is waiting for a follow-back", this::openFriends));
        }
        show(player, "Follow back", "These players follow you", actions, 2, this::openFriends);
    }

    // --- Pay ---

    public void openPayPlayers(Player player) {
        if (!plugin.guis().canUseEconomy(player)) {
            Messages.send(plugin, player, plugin.getConfig().getString("messages.economy-disabled", ""));
            return;
        }
        List<ActionButton> actions = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            String name = online.getName();
            UUID id = online.getUniqueId();
            actions.add(btn(name, "Balance: " + Messages.money(plugin, plugin.economy().getBalance(id)),
                    p -> openPayAmounts(p, id, name)));
        }
        if (actions.isEmpty()) {
            actions.add(btn("No players", "Nobody else is online", this::openMain));
        }
        show(player, "Pay", "Pick a player", actions, 2, this::openMain);
    }

    public void openPayAmounts(Player player, UUID target, String targetName) {
        List<ActionButton> actions = new ArrayList<>();
        for (double amount : PAY_PRESETS) {
            double pay = amount;
            actions.add(btn(formatPreset(pay), "Send " + Messages.money(plugin, pay), p -> {
                pay(p, target, targetName, pay);
                openMain(p);
            }));
        }
        actions.add(btn("Custom", "Type amount in chat", p -> {
            p.closeDialog();
            plugin.chatInput().beginPayAmount(p, target);
        }));
        show(player, "Pay " + targetName, "Choose an amount", actions, 2, this::openPayPlayers);
    }

    public void pay(Player payer, UUID target, String targetName, double amount) {
        if (!plugin.economy().isEconomyEnabled(payer.getUniqueId())
                || !plugin.economy().isEconomyEnabled(target)) {
            Messages.send(plugin, payer, plugin.getConfig().getString("messages.economy-disabled", ""));
            return;
        }
        if (!plugin.economy().transfer(payer.getUniqueId(), target, amount)) {
            Messages.send(plugin, payer, plugin.getConfig().getString("messages.insufficient-funds", ""));
            return;
        }
        Messages.send(plugin, payer, "<green>Paid " + Messages.money(plugin, amount) + " to " + targetName + ".</green>");
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            Messages.send(plugin, online, "<green>You received " + Messages.money(plugin, amount)
                    + " from " + payer.getName() + ".</green>");
        }
        plugin.logAction(payer.getName() + " paid " + amount + " to " + targetName);
        plugin.settings().refreshMoneyNametag(payer);
        if (online != null) {
            plugin.settings().refreshMoneyNametag(online);
        }
    }

    // --- Stats ---

    public void openStatsPlayers(Player player) {
        List<ActionButton> actions = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            String name = online.getName();
            UUID id = online.getUniqueId();
            actions.add(btn(name, "View stats", p -> openStatsView(p, id, name)));
        }
        show(player, "Stats", "Pick a player", actions, 2, this::openMain);
    }

    public void openStatsView(Player viewer, UUID target, String targetName) {
        PlayerSettings privacy = plugin.settings().get(target);
        StatsService.PlayerStats stats = plugin.stats().get(target);
        List<String> lines = new ArrayList<>();
        if (privacy.showMoney()) {
            lines.add("Money: " + Messages.money(plugin, plugin.economy().getBalance(target)));
        } else {
            lines.add("Money: Hidden");
        }
        if (privacy.showKills()) {
            lines.add("Kills: " + stats.kills());
        } else {
            lines.add("Kills: Hidden");
        }
        if (privacy.showDeaths()) {
            lines.add("Deaths: " + stats.deaths());
        } else {
            lines.add("Deaths: Hidden");
        }
        if (privacy.showPlaytime()) {
            lines.add("Playtime: " + StatsService.formatPlaytime(stats.playtimeMs()));
        } else {
            lines.add("Playtime: Hidden");
        }
        if (privacy.showJob()) {
            lines.add("Job: " + jobStatus(target));
        } else {
            lines.add("Job: Hidden");
        }
        lines.add("Blocks placed: " + stats.blocksPlaced());
        lines.add("Blocks broken: " + stats.blocksBroken());
        lines.add("Mobs killed: " + stats.mobsKilled());
        showNotice(viewer, targetName + " Stats", String.join("\n", lines),
                List.of(btn("Back", "Return", this::openStatsPlayers)));
    }

    private String jobStatus(UUID uuid) {
        JobService.PlayerJob job = plugin.jobs().getPlayerJob(uuid);
        if (job.hasJob()) {
            JobDefinition def = plugin.jobs().get(job.jobId());
            return "Active worker (" + (def == null ? job.jobId() : def.displayName()) + ")";
        }
        if (plugin.jobs().isOnCooldown(uuid)) {
            long days = TimeUnit.MILLISECONDS.toDays(plugin.jobs().cooldownRemainingMs(uuid)) + 1;
            return "Fired cooldown (~" + days + "d)";
        }
        return "None";
    }

    // --- Settings ---

    public void openSettings(Player player) {
        List<ActionButton> actions = List.of(
                btn("Chat", "Who you see in chat", this::openSettingsChat),
                btn("Notifications", "Kill/death message filter", this::openSettingsNotifs),
                btn("Visuals", "Night vision & money nametag", this::openSettingsVisuals),
                btn("Privacy", "Profile fields & instant TPA", this::openSettingsPrivacy),
                btn("General", "Auction/Sell & Jobs preference", this::openSettingsGeneral)
        );
        show(player, "Settings", "Stored per player", actions, 2, this::openMain);
    }

    private void openSettingsChat(Player player) {
        PlayerSettings s = plugin.settings().get(player.getUniqueId());
        List<ActionButton> actions = List.of(
                toggleBtn("Chat: FRIENDS", s.chatFilter() == PlayerSettings.Filter.FRIENDS, p -> {
                    saveSettings(p, cur -> new PlayerSettings(
                            PlayerSettings.Filter.FRIENDS, cur.notifFilter(), cur.nightVision(), cur.moneyNametag(),
                            cur.showMoney(), cur.showKills(), cur.showDeaths(), cur.showPlaytime(), cur.showJob(),
                            cur.instantTpa(), cur.instantTpaHere(), cur.auctionEnabled(), cur.jobsEnabled()));
                    openSettingsChat(p);
                }),
                toggleBtn("Chat: EVERYONE", s.chatFilter() == PlayerSettings.Filter.EVERYONE, p -> {
                    saveSettings(p, cur -> new PlayerSettings(
                            PlayerSettings.Filter.EVERYONE, cur.notifFilter(), cur.nightVision(), cur.moneyNametag(),
                            cur.showMoney(), cur.showKills(), cur.showDeaths(), cur.showPlaytime(), cur.showJob(),
                            cur.instantTpa(), cur.instantTpaHere(), cur.auctionEnabled(), cur.jobsEnabled()));
                    openSettingsChat(p);
                })
        );
        show(player, "Chat Filter", "FRIENDS or EVERYONE", actions, 1, this::openSettings);
    }

    private void openSettingsNotifs(Player player) {
        PlayerSettings s = plugin.settings().get(player.getUniqueId());
        List<ActionButton> actions = List.of(
                toggleBtn("Notifs: FRIENDS", s.notifFilter() == PlayerSettings.Filter.FRIENDS, p -> {
                    saveSettings(p, cur -> new PlayerSettings(
                            cur.chatFilter(), PlayerSettings.Filter.FRIENDS, cur.nightVision(), cur.moneyNametag(),
                            cur.showMoney(), cur.showKills(), cur.showDeaths(), cur.showPlaytime(), cur.showJob(),
                            cur.instantTpa(), cur.instantTpaHere(), cur.auctionEnabled(), cur.jobsEnabled()));
                    openSettingsNotifs(p);
                }),
                toggleBtn("Notifs: EVERYONE", s.notifFilter() == PlayerSettings.Filter.EVERYONE, p -> {
                    saveSettings(p, cur -> new PlayerSettings(
                            cur.chatFilter(), PlayerSettings.Filter.EVERYONE, cur.nightVision(), cur.moneyNametag(),
                            cur.showMoney(), cur.showKills(), cur.showDeaths(), cur.showPlaytime(), cur.showJob(),
                            cur.instantTpa(), cur.instantTpaHere(), cur.auctionEnabled(), cur.jobsEnabled()));
                    openSettingsNotifs(p);
                })
        );
        show(player, "Notifications", "Kill/death visibility", actions, 1, this::openSettings);
    }

    private void openSettingsVisuals(Player player) {
        PlayerSettings s = plugin.settings().get(player.getUniqueId());
        List<ActionButton> actions = List.of(
                toggleBtn("Night Vision", s.nightVision(), p -> {
                    saveSettings(p, cur -> new PlayerSettings(
                            cur.chatFilter(), cur.notifFilter(), !cur.nightVision(), cur.moneyNametag(),
                            cur.showMoney(), cur.showKills(), cur.showDeaths(), cur.showPlaytime(), cur.showJob(),
                            cur.instantTpa(), cur.instantTpaHere(), cur.auctionEnabled(), cur.jobsEnabled()));
                    plugin.settings().applyVisuals(p);
                    openSettingsVisuals(p);
                }),
                toggleBtn("Money Nametag", s.moneyNametag(), p -> {
                    saveSettings(p, cur -> new PlayerSettings(
                            cur.chatFilter(), cur.notifFilter(), cur.nightVision(), !cur.moneyNametag(),
                            cur.showMoney(), cur.showKills(), cur.showDeaths(), cur.showPlaytime(), cur.showJob(),
                            cur.instantTpa(), cur.instantTpaHere(), cur.auctionEnabled(), cur.jobsEnabled()));
                    plugin.settings().applyVisuals(p);
                    openSettingsVisuals(p);
                })
        );
        show(player, "Visuals", "Client-side helpers", actions, 1, this::openSettings);
    }

    private void openSettingsPrivacy(Player player) {
        PlayerSettings s = plugin.settings().get(player.getUniqueId());
        List<ActionButton> actions = new ArrayList<>();
        actions.add(toggleBtn("Show Money", s.showMoney(), p -> flipPrivacy(p, "money")));
        actions.add(toggleBtn("Show Kills", s.showKills(), p -> flipPrivacy(p, "kills")));
        actions.add(toggleBtn("Show Deaths", s.showDeaths(), p -> flipPrivacy(p, "deaths")));
        actions.add(toggleBtn("Show Playtime", s.showPlaytime(), p -> flipPrivacy(p, "playtime")));
        actions.add(toggleBtn("Show Job", s.showJob(), p -> flipPrivacy(p, "job")));
        actions.add(btn("Instant TPA: " + s.instantTpa().name(), "Cycle ANYONE/FRIENDS/NOBODY", p -> {
            saveSettings(p, cur -> new PlayerSettings(
                    cur.chatFilter(), cur.notifFilter(), cur.nightVision(), cur.moneyNametag(),
                    cur.showMoney(), cur.showKills(), cur.showDeaths(), cur.showPlaytime(), cur.showJob(),
                    cycleInstant(cur.instantTpa()), cur.instantTpaHere(), cur.auctionEnabled(), cur.jobsEnabled()));
            openSettingsPrivacy(p);
        }));
        actions.add(btn("Instant TPAHere: " + s.instantTpaHere().name(), "Cycle ANYONE/FRIENDS/NOBODY", p -> {
            saveSettings(p, cur -> new PlayerSettings(
                    cur.chatFilter(), cur.notifFilter(), cur.nightVision(), cur.moneyNametag(),
                    cur.showMoney(), cur.showKills(), cur.showDeaths(), cur.showPlaytime(), cur.showJob(),
                    cur.instantTpa(), cycleInstant(cur.instantTpaHere()), cur.auctionEnabled(), cur.jobsEnabled()));
            openSettingsPrivacy(p);
        }));
        show(player, "Privacy", "Who can see your profile / instant TP", actions, 2, this::openSettings);
    }

    private void flipPrivacy(Player player, String field) {
        saveSettings(player, cur -> switch (field) {
            case "money" -> new PlayerSettings(cur.chatFilter(), cur.notifFilter(), cur.nightVision(), cur.moneyNametag(),
                    !cur.showMoney(), cur.showKills(), cur.showDeaths(), cur.showPlaytime(), cur.showJob(),
                    cur.instantTpa(), cur.instantTpaHere(), cur.auctionEnabled(), cur.jobsEnabled());
            case "kills" -> new PlayerSettings(cur.chatFilter(), cur.notifFilter(), cur.nightVision(), cur.moneyNametag(),
                    cur.showMoney(), !cur.showKills(), cur.showDeaths(), cur.showPlaytime(), cur.showJob(),
                    cur.instantTpa(), cur.instantTpaHere(), cur.auctionEnabled(), cur.jobsEnabled());
            case "deaths" -> new PlayerSettings(cur.chatFilter(), cur.notifFilter(), cur.nightVision(), cur.moneyNametag(),
                    cur.showMoney(), cur.showKills(), !cur.showDeaths(), cur.showPlaytime(), cur.showJob(),
                    cur.instantTpa(), cur.instantTpaHere(), cur.auctionEnabled(), cur.jobsEnabled());
            case "playtime" -> new PlayerSettings(cur.chatFilter(), cur.notifFilter(), cur.nightVision(), cur.moneyNametag(),
                    cur.showMoney(), cur.showKills(), cur.showDeaths(), !cur.showPlaytime(), cur.showJob(),
                    cur.instantTpa(), cur.instantTpaHere(), cur.auctionEnabled(), cur.jobsEnabled());
            default -> new PlayerSettings(cur.chatFilter(), cur.notifFilter(), cur.nightVision(), cur.moneyNametag(),
                    cur.showMoney(), cur.showKills(), cur.showDeaths(), cur.showPlaytime(), !cur.showJob(),
                    cur.instantTpa(), cur.instantTpaHere(), cur.auctionEnabled(), cur.jobsEnabled());
        });
        openSettingsPrivacy(player);
    }

    private void openSettingsGeneral(Player player) {
        PlayerSettings s = plugin.settings().get(player.getUniqueId());
        List<ActionButton> actions = List.of(
                toggleBtn("Auction/Sell enabled", s.auctionEnabled(), p -> {
                    saveSettings(p, cur -> new PlayerSettings(
                            cur.chatFilter(), cur.notifFilter(), cur.nightVision(), cur.moneyNametag(),
                            cur.showMoney(), cur.showKills(), cur.showDeaths(), cur.showPlaytime(), cur.showJob(),
                            cur.instantTpa(), cur.instantTpaHere(), !cur.auctionEnabled(), cur.jobsEnabled()));
                    openSettingsGeneral(p);
                }),
                toggleBtn("Jobs enabled", s.jobsEnabled(), p -> {
                    saveSettings(p, cur -> new PlayerSettings(
                            cur.chatFilter(), cur.notifFilter(), cur.nightVision(), cur.moneyNametag(),
                            cur.showMoney(), cur.showKills(), cur.showDeaths(), cur.showPlaytime(), cur.showJob(),
                            cur.instantTpa(), cur.instantTpaHere(), cur.auctionEnabled(), !cur.jobsEnabled()));
                    openSettingsGeneral(p);
                })
        );
        show(player, "General", "Personal feature toggles", actions, 1, this::openSettings);
    }

    private void saveSettings(Player player, java.util.function.Function<PlayerSettings, PlayerSettings> fn) {
        PlayerSettings next = fn.apply(plugin.settings().get(player.getUniqueId()));
        plugin.settings().save(player.getUniqueId(), next);
        plugin.logAction(player.getName() + " updated settings");
    }

    private static PlayerSettings.InstantAllow cycleInstant(PlayerSettings.InstantAllow current) {
        return switch (current) {
            case ANYONE -> PlayerSettings.InstantAllow.FRIENDS;
            case FRIENDS -> PlayerSettings.InstantAllow.NOBODY;
            case NOBODY -> PlayerSettings.InstantAllow.ANYONE;
        };
    }

    // --- Dialog helpers ---

    private void show(Player player, String title, String body, List<ActionButton> actions, int columns,
                      Consumer<Player> back) {
        // NONE: stay on this dialog until the next showDialog/close, without the
        // vanilla "Waiting for Server" / Back (3s) overlay.
        ActionButton exit = back == null
                ? btn("Close", "Close menu", p -> p.closeDialog())
                : btn("Back", "Go back", back);
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(title))
                        .body(List.of(DialogBody.plainMessage(Component.text(body))))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build())
                .type(DialogType.multiAction(actions, exit, columns)));
        player.showDialog(dialog);
    }

    private void showNotice(Player player, String title, String body, List<ActionButton> actions) {
        ActionButton ok = actions.isEmpty()
                ? btn("Close", "Close", p -> p.closeDialog())
                : actions.getFirst();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(title))
                        .body(List.of(DialogBody.plainMessage(Component.text(body))))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build())
                .type(DialogType.notice(ok)));
        player.showDialog(dialog);
    }

    private ActionButton btn(String label, String tooltip, Consumer<Player> click) {
        return ActionButton.builder(Component.text(label))
                .tooltip(Component.text(tooltip))
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player player)) {
                        return;
                    }
                    Runnable run = () -> {
                        try {
                            click.accept(player);
                        } catch (Exception e) {
                            plugin.getLogger().warning("Menu action failed: " + e.getMessage());
                            player.closeDialog();
                        }
                    };
                    if (Bukkit.isPrimaryThread()) {
                        run.run();
                    } else {
                        Bukkit.getScheduler().runTask(plugin, run);
                    }
                }, ClickCallback.Options.builder()
                        .uses(ClickCallback.UNLIMITED_USES)
                        .lifetime(java.time.Duration.ofHours(1))
                        .build()))
                .build();
    }

    private ActionButton toggleBtn(String label, boolean on, Consumer<Player> click) {
        String prefix = on ? "[ON] " : "[OFF] ";
        return btn(prefix + label, "Click to change", click);
    }

    private static String prettyBoard(StatsService.LeaderboardType type) {
        return switch (type) {
            case MONEY -> "Money";
            case PLAYTIME -> "Playtime";
            case KILLS -> "Kills";
            case DEATHS -> "Deaths";
            case BLOCKS_PLACED -> "Blocks Placed";
            case BLOCKS_BROKEN -> "Blocks Broken";
            case MOBS_KILLED -> "Mobs Killed";
        };
    }

    private String formatBoardValue(StatsService.LeaderboardType type, long value) {
        return switch (type) {
            case MONEY -> Messages.money(plugin, value);
            case PLAYTIME -> StatsService.formatPlaytime(value);
            default -> String.valueOf(value);
        };
    }

    private static String formatPreset(double amount) {
        if (amount >= 1000) {
            double k = amount / 1000.0;
            if (Math.abs(k - Math.rint(k)) < 0.001) {
                return ((int) Math.rint(k)) + "K";
            }
            return String.format(Locale.US, "%.1fK", k);
        }
        return String.valueOf((int) amount);
    }
}
