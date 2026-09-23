package com.ecostg.paper.menu;

import com.ecostg.paper.EcoSTGPlugin;
import com.ecostg.paper.job.JobDefinition;
import com.ecostg.paper.job.JobService;
import com.ecostg.paper.util.Messages;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.key.Key;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.ServerLinks;
import org.bukkit.entity.Player;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
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
    public static final Key HUB_ENDER_CHEST = Key.key("ecostg:hub_ender_chest");
    public static final Key HUB_SIT_LAY = Key.key("ecostg:hub_sit_lay");
    public static final Key HUB_GAMEMODE = Key.key("ecostg:hub_gamemode");
    public static final Key HUB_CLOSE = Key.key("ecostg:hub_close");
    private static final String GSIT_PLUGIN = "GSit";

    private static final double[] PAY_PRESETS = {1000, 3400, 10000, 17000, 34000, 100000};
    private static final int BTN_WIDTH = 150;
    private static final int WIDE_BUTTON = 300;
    private static final int BODY_WIDTH = 300;
    private static final int HOMES_PER_PAGE = 4;

    private enum FriendFilter {
        ALL, FRIENDS, FOLLOWING, ONLINE
    }

    private final EcoSTGPlugin plugin;
    private final Map<UUID, FriendFilter> friendFilters = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> homesPage = new ConcurrentHashMap<>();
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
        actions.add(btn("Sell", "List items on the auction house", this::openSellSafe));
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
        actions.add(btn("Ender Chest", "Open your ender chest", this::openEnderChest));
        if (isGsitEnabled()) {
            actions.add(btn("Sit/lay", "Sit, crawl, lay, or spin (GSit)", this::openSitLay));
        }
        if (player.isOp()) {
            actions.add(btn("Gamemode", "Change your gamemode (OP)", this::openGamemode));
        }

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
        } else if (key.equals(HUB_ENDER_CHEST)) {
            openEnderChest(player);
        } else if (key.equals(HUB_SIT_LAY)) {
            openSitLay(player);
        } else if (key.equals(HUB_GAMEMODE)) {
            openGamemode(player);
        } else if (key.equals(HUB_CLOSE)) {
            player.closeDialog();
        } else {
            openMain(player);
        }
    }

    // --- Homes ---

    public void openHomes(Player player) {
        homesPage.put(player.getUniqueId(), 0);
        openHomesPage(player);
    }

    private void openHomesPage(Player player) {
        List<HomeService.Home> homes = plugin.homes().list(player.getUniqueId());
        int page = homesPage.getOrDefault(player.getUniqueId(), 0);
        int pages = Math.max(1, (int) Math.ceil(homes.size() / (double) HOMES_PER_PAGE));
        if (page >= pages) {
            page = 0;
            homesPage.put(player.getUniqueId(), 0);
        }
        int from = page * HOMES_PER_PAGE;
        int to = Math.min(homes.size(), from + HOMES_PER_PAGE);

        List<ActionButton> actions = new ArrayList<>();
        for (int i = from; i < to; i++) {
            HomeService.Home home = homes.get(i);
            String name = home.name();
            actions.add(btn(Component.text(name, home.textColor()), "Open " + name,
                    p -> openHomeDetail(p, name)));
        }
        int max = plugin.homes().maxHomes(player);
        if (homes.size() < max) {
            actions.add(btn("New Home", "Save your current location", p -> {
                String next = plugin.homes().nextDefaultName(p.getUniqueId());
                int pMax = plugin.homes().maxHomes(p);
                if (plugin.homes().setHome(p, next)) {
                    Messages.send(plugin, p, "<green>Home '" + next + "' set.</green>");
                    openHomeDetail(p, next);
                } else {
                    Messages.send(plugin, p, "<red>Could not set home (max " + pMax + ").</red>");
                    openHomesPage(p);
                }
            }));
        }
        if (homes.size() > HOMES_PER_PAGE) {
            int nextPage = page;
            actions.add(btn("Show More", "More homes", p -> {
                homesPage.put(p.getUniqueId(), (nextPage + 1) % Math.max(1,
                        (int) Math.ceil(plugin.homes().list(p.getUniqueId()).size() / (double) HOMES_PER_PAGE)));
                openHomesPage(p);
            }));
        }
        if (actions.isEmpty()) {
            actions.add(btn("No homes", "Use New Home to set one", this::openHomesPage));
        }
        show(player, Component.text("Homes"), List.of(), actions, 2, this::openMain);
    }

    public void openHomeDetail(Player player, String homeName) {
        HomeService.Home home = plugin.homes().get(player.getUniqueId(), homeName);
        if (home == null) {
            openHomesPage(player);
            return;
        }
        String name = home.name();
        List<ActionButton> actions = new ArrayList<>();
        actions.add(btn("Teleport", "Go to this home", p -> {
            if (plugin.homes().teleport(p, name)) {
                Messages.send(plugin, p, "<green>Teleported to home '" + name + "'.</green>");
                p.closeDialog();
            } else {
                openHomesPage(p);
            }
        }));
        actions.add(btn("Change Color", "Preset or custom HEX", p -> openHomeColorPicker(p, name)));
        actions.add(btn("Rename", "Change this home's name", p -> openHomeRename(p, name)));
        actions.add(btn(Component.text("Delete", NamedTextColor.RED), "Remove this home", p -> {
            plugin.homes().deleteHome(p.getUniqueId(), name);
            Messages.send(plugin, p, "<yellow>Deleted home '" + name + "'.</yellow>");
            openHomesPage(p);
        }));
        show(player, Component.text(name, home.textColor()), List.of(
                textBody(Component.text("Color: " + HomeService.presetLabel(home.colorHex()), home.textColor()))
        ), actions, 2, this::openHomesPage);
    }

    private void openHomeRename(Player player, String homeName) {
        HomeService.Home home = plugin.homes().get(player.getUniqueId(), homeName);
        if (home == null) {
            openHomesPage(player);
            return;
        }
        String current = home.name();
        DialogInput nameInput = DialogInput.text("new_name", Component.text("New Name"))
                .initial(current)
                .maxLength(32)
                .width(300)
                .build();
        List<ActionButton> actions = List.of(
                wideBtn("Save", "Save the new name", (p, view) -> {
                    String typed = view.getText("new_name");
                    String sanitized = HomeService.sanitize(typed);
                    if (sanitized == null) {
                        Messages.send(plugin, p, "<red>Name must be 1-32 letters, numbers, spaces, _ or -.</red>");
                        openHomeRename(p, current);
                        return;
                    }
                    if (!plugin.homes().rename(p.getUniqueId(), current, sanitized)) {
                        Messages.send(plugin, p, "<red>Could not rename (name already used?).</red>");
                        openHomeRename(p, current);
                        return;
                    }
                    Messages.send(plugin, p, "<green>Home renamed to '" + sanitized + "'.</green>");
                    plugin.logAction(p.getName() + " renamed home '" + current + "' to '" + sanitized + "'");
                    openHomeDetail(p, sanitized);
                })
        );
        show(player, Component.text("Rename"), List.of(), List.of(nameInput), actions, 1,
                p -> openHomeDetail(p, current), true);
    }

    private void openHomeColorPicker(Player player, String homeName) {
        HomeService.Home home = plugin.homes().get(player.getUniqueId(), homeName);
        if (home == null) {
            openHomesPage(player);
            return;
        }
        String name = home.name();
        String currentHex = home.colorHex();
        List<ActionButton> actions = new ArrayList<>();
        for (HomeService.ColorPreset preset : HomeService.COLOR_PRESETS) {
            String hex = preset.hex();
            String label = preset.name();
            actions.add(btn(Component.text(label, preset.textColor()), hex, p -> {
                plugin.homes().setColor(p.getUniqueId(), name, hex);
                Messages.send(plugin, p, "<green>Color set to " + label + ".</green>");
                openHomeDetail(p, name);
            }));
        }
        actions.add(wideBtn("Apply HEX", "Use the custom color below", (p, view) -> {
            String typed = view.getText("home_hex");
            String hex = HomeService.parseHexInput(typed);
            if (hex == null) {
                Messages.send(plugin, p, "<red>Enter a valid HEX color like #55FFFF.</red>");
                openHomeColorPicker(p, name);
                return;
            }
            plugin.homes().setColor(p.getUniqueId(), name, hex);
            Messages.send(plugin, p, "<green>Color set to " + hex + ".</green>");
            openHomeDetail(p, name);
        }));

        DialogInput hexInput = DialogInput.text("home_hex", Component.text("Custom HEX (#RRGGBB)"))
                .initial(currentHex)
                .maxLength(7)
                .width(300)
                .build();
        show(player, Component.text("Choose Color"),
                List.of(textBody(Component.text("Current: " + HomeService.presetLabel(currentHex), home.textColor()))),
                List.of(hexInput), actions, 2, p -> openHomeDetail(p, name), true);
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
        List<DialogBody> bodies = new ArrayList<>();
        int rank = 1;
        for (StatsService.LeaderboardEntry entry : top) {
            bodies.add(textBody(leaderboardLine(rank, entry, type)));
            rank++;
        }
        if (top.isEmpty()) {
            bodies.add(textBody(Component.text("No data yet.", NamedTextColor.GRAY)));
        }
        List<ActionButton> actions = List.of(btn("Back", "Return", this::openLeaderboards));
        show(player, Component.text("Top " + prettyBoard(type)), bodies, actions, 1, this::openLeaderboards);
    }

    private Component leaderboardLine(int rank, StatsService.LeaderboardEntry entry,
                                      StatsService.LeaderboardType type) {
        TextColor color = rankColor(rank);
        return Component.text("#" + rank + " ", color)
                .append(Component.object(ObjectContents.playerHead(entry.uuid())))
                .append(Component.text(" " + entry.name(), color))
                .append(Component.text(" - " + formatBoardValue(type, entry.value()), NamedTextColor.WHITE));
    }

    private static TextColor rankColor(int rank) {
        return switch (rank) {
            case 1 -> NamedTextColor.GOLD;
            case 3 -> TextColor.color(0xCD7F32);
            default -> NamedTextColor.WHITE;
        };
    }

    // --- Friends ---

    public void openFriends(Player player) {
        UUID id = player.getUniqueId();
        FriendFilter filter = friendFilters.getOrDefault(id, FriendFilter.ALL);
        int friends = plugin.friends().listFriends(id).size();
        int following = plugin.friends().listFollowing(id).size();

        List<ActionButton> actions = new ArrayList<>();
        actions.add(btn("Filter", "Filter: " + prettyFilter(filter) + " (click to cycle)", p -> {
            friendFilters.put(id, nextFilter(filter));
            openFriends(p);
        }));
        actions.add(btn("Search", "Search by player name", this::openFriendsSearch));
        for (FriendService.FriendEntry entry : filteredFriends(id, filter)) {
            UUID target = entry.uuid();
            String name = entry.name();
            actions.add(btn(withHeadIcon(target, name), "Open profile", p -> openFriendProfile(p, target, name)));
        }
        actions.add(btn(Component.text("+ Follow", NamedTextColor.GREEN), "Follow an online player",
                this::openFriendsAdd));
        show(player, "Friends  " + friends + " friends / " + following + " following",
                "", actions, 3, this::openMain);
    }

    private List<FriendService.FriendEntry> filteredFriends(UUID id, FriendFilter filter) {
        return switch (filter) {
            case ALL -> plugin.friends().listFollowing(id);
            case FRIENDS -> plugin.friends().listFriends(id);
            case FOLLOWING -> plugin.friends().listPendingFollowing(id);
            case ONLINE -> plugin.friends().listFollowing(id).stream()
                    .filter(entry -> Bukkit.getPlayer(entry.uuid()) != null)
                    .toList();
        };
    }

    private static FriendFilter nextFilter(FriendFilter current) {
        return switch (current) {
            case ALL -> FriendFilter.FRIENDS;
            case FRIENDS -> FriendFilter.FOLLOWING;
            case FOLLOWING -> FriendFilter.ONLINE;
            case ONLINE -> FriendFilter.ALL;
        };
    }

    private static String prettyFilter(FriendFilter filter) {
        return switch (filter) {
            case ALL -> "All";
            case FRIENDS -> "Friends";
            case FOLLOWING -> "Following";
            case ONLINE -> "Online";
        };
    }

    private void openFriendsSearch(Player player) {
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Search Friends"))
                        .inputs(List.of(DialogInput.text("player_name", Component.text("Player name")).build()))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build())
                .type(DialogType.confirmation(
                        btn(Component.text("Cancel", NamedTextColor.RED), "Go back", WIDE_BUTTON,
                                (p, view) -> openFriends(p)),
                        btn(Component.text("Search", NamedTextColor.GREEN), "Find player",
                                WIDE_BUTTON, (p, view) -> handleFriendSearch(p, view.getText("player_name")))
                )));
        player.showDialog(dialog);
    }

    private void handleFriendSearch(Player player, String raw) {
        if (raw == null || raw.isBlank()) {
            Messages.send(plugin, player, "<red>Enter a player name.</red>");
            openFriendsSearch(player);
            return;
        }
        String query = raw.trim();
        UUID uuid = plugin.economy().findUuidByName(query);
        if (uuid == null) {
            String lower = query.toLowerCase(Locale.ROOT);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                    uuid = online.getUniqueId();
                    break;
                }
            }
        }
        if (uuid == null) {
            Messages.send(plugin, player, plugin.getConfig().getString("messages.player-not-found", ""));
            openFriendsSearch(player);
            return;
        }
        String name = plugin.economy().getName(uuid);
        plugin.economy().ensurePlayer(uuid, name);
        openFriendProfile(player, uuid, name);
    }

    public void openFriendProfile(Player viewer, UUID target, String targetName) {
        boolean self = viewer.getUniqueId().equals(target);
        boolean following = !self && plugin.friends().isFollowing(viewer.getUniqueId(), target);

        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(textBody(withHeadIcon(target, targetName)));
        String description = plugin.settings().getDescription(target);
        if (!description.isBlank()) {
            bodies.add(textBody(Component.text(description, NamedTextColor.GRAY)));
        } else if (self) {
            bodies.add(textBody(Component.text("No description yet.", NamedTextColor.DARK_GRAY)));
        }

        List<ActionButton> actions = new ArrayList<>();
        actions.add(wideBtn("View Stats", "Open stats preview",
                p -> openStatsPreview(p, target, targetName, back -> openFriendProfile(back, target, targetName))));
        if (self) {
            actions.add(wideBtn("Edit Description", "Set a short profile bio",
                    p -> openDescriptionEditor(p, back -> openFriendProfile(back, target, targetName))));
        }
        if (!self) {
            actions.add(wideBtn("Pay", "Send money", p -> {
                if (!plugin.guis().canUseEconomy(p)) {
                    Messages.send(plugin, p, plugin.getConfig().getString("messages.economy-disabled", ""));
                    openFriendProfile(p, target, targetName);
                    return;
                }
                if (Bukkit.getPlayer(target) == null) {
                    Messages.send(plugin, p, "<red>That player is offline.</red>");
                    openFriendProfile(p, target, targetName);
                    return;
                }
                openPayAmounts(p, target, targetName, back -> openFriendProfile(back, target, targetName));
            }));
            actions.add(wideBtn("Teleport", "Send a TPA request", p -> {
                Player targetPlayer = Bukkit.getPlayer(target);
                if (targetPlayer == null) {
                    Messages.send(plugin, p, "<red>That player is offline.</red>");
                    openFriendProfile(p, target, targetName);
                    return;
                }
                plugin.tpa().request(p, targetPlayer, TpaService.Type.TPA);
                p.closeDialog();
            }));
        }
        if (!self && following) {
            actions.add(btn(Component.text("Unfollow", NamedTextColor.RED), "Stop following", WIDE_BUTTON,
                    (p, view) -> {
                        plugin.friends().unfollow(p.getUniqueId(), target);
                        openFriends(p);
                    }));
        } else if (!self) {
            actions.add(btn(Component.text("+ Follow", NamedTextColor.GREEN), "Follow this player", WIDE_BUTTON,
                    (p, view) -> {
                        plugin.friends().follow(p.getUniqueId(), target);
                        openFriendProfile(p, target, targetName);
                    }));
        }
        show(viewer, Component.text(targetName + " Friends"), bodies, actions, 1, this::openFriends);
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
            actions.add(btn(withHeadIcon(id, name), theyFollow ? "Follow back to become friends" : "Follow this player", p -> {
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
            actions.add(btn(withHeadIcon(id, name), "Balance: " + Messages.money(plugin, plugin.economy().getBalance(id)),
                    p -> openPayAmounts(p, id, name)));
        }
        if (actions.isEmpty()) {
            actions.add(btn("No players", "Nobody else is online", this::openMain));
        }
        show(player, "Pay", "Pick a player", actions, 2, this::openMain);
    }

    public void openPayAmounts(Player player, UUID target, String targetName) {
        openPayAmounts(player, target, targetName, this::openPayPlayers);
    }

    public void openPayAmounts(Player player, UUID target, String targetName, Consumer<Player> back) {
        List<ActionButton> actions = new ArrayList<>();
        for (double amount : PAY_PRESETS) {
            double pay = amount;
            actions.add(btn(formatPreset(pay), "Send " + Messages.money(plugin, pay), p -> {
                pay(p, target, targetName, pay);
                back.accept(p);
            }));
        }
        actions.add(btn("Custom", "Type amount in chat", p -> {
            p.closeDialog();
            plugin.chatInput().beginPayAmount(p, target);
        }));
        show(player, "Pay " + targetName, "Choose an amount", actions, 2, back);
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
            actions.add(btn(withHeadIcon(id, name), "View stats", p -> openStatsPreview(p, id, name, this::openStatsPlayers)));
        }
        show(player, "Stats", "Pick a player", actions, 2, this::openMain);
    }

    public void openStatsView(Player viewer, UUID target, String targetName) {
        openStatsPreview(viewer, target, targetName, this::openMain);
    }

    public void openStatsPreview(Player viewer, UUID target, String targetName, Consumer<Player> back) {
        PlayerSettings privacy = plugin.settings().get(target);
        StatsService.PlayerStats stats = plugin.stats().get(target);
        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(textBody(withHeadIcon(target, targetName)));
        String description = plugin.settings().getDescription(target);
        if (!description.isBlank()) {
            bodies.add(textBody(Component.text(description, NamedTextColor.GRAY)));
        }
        if (privacy.showMoney()) {
            bodies.add(textBody(Messages.mm(
                    "Money: <green>" + Messages.compactMoney(plugin, plugin.economy().getBalance(target)) + "</green>")));
        } else {
            bodies.add(textBody(Component.text("Money: Hidden")));
        }
        bodies.add(textBody(Component.text(
                "Kills: " + (privacy.showKills() ? String.valueOf(stats.kills()) : "Hidden"))));
        bodies.add(textBody(Component.text(
                "Deaths: " + (privacy.showDeaths() ? String.valueOf(stats.deaths()) : "Hidden"))));
        bodies.add(textBody(Component.text(
                "Playtime: " + (privacy.showPlaytime() ? StatsService.formatPlaytime(stats.playtimeMs()) : "Hidden"))));

        List<ActionButton> actions = new ArrayList<>();
        if (viewer.getUniqueId().equals(target)) {
            actions.add(wideBtn("Edit Description", "Set a short profile bio",
                    p -> openDescriptionEditor(p, editBack -> openStatsPreview(editBack, target, targetName, back))));
        }
        show(viewer, Component.text(targetName), bodies, actions, 1, back);
    }

    public void openStatsFull(Player viewer, UUID target, String targetName, Consumer<Player> back) {
        PlayerSettings privacy = plugin.settings().get(target);
        StatsService.PlayerStats stats = plugin.stats().get(target);
        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(textBody(withHeadIcon(target, targetName)));
        String description = plugin.settings().getDescription(target);
        if (!description.isBlank()) {
            bodies.add(textBody(Component.text(description, NamedTextColor.GRAY)));
        }
        if (privacy.showMoney()) {
            bodies.add(textBody(Component.text(
                    "Money: " + Messages.money(plugin, plugin.economy().getBalance(target)))));
        } else {
            bodies.add(textBody(Component.text("Money: Hidden")));
        }
        bodies.add(textBody(Component.text(
                "Kills: " + (privacy.showKills() ? String.valueOf(stats.kills()) : "Hidden"))));
        bodies.add(textBody(Component.text(
                "Deaths: " + (privacy.showDeaths() ? String.valueOf(stats.deaths()) : "Hidden"))));
        bodies.add(textBody(Component.text(
                "Playtime: " + (privacy.showPlaytime() ? StatsService.formatPlaytime(stats.playtimeMs()) : "Hidden"))));
        bodies.add(textBody(Component.text(
                "Job: " + (privacy.showJob() ? jobStatus(target) : "Hidden"))));
        bodies.add(textBody(Component.text("Blocks placed: " + stats.blocksPlaced())));
        bodies.add(textBody(Component.text("Blocks broken: " + stats.blocksBroken())));
        bodies.add(textBody(Component.text("Mobs killed: " + stats.mobsKilled())));
        show(viewer, Component.text(targetName + " Profile"), bodies, List.of(), 1, back);
    }

    public void openDescriptionEditor(Player player, Consumer<Player> back) {
        String current = plugin.settings().getDescription(player.getUniqueId());
        DialogInput input = DialogInput.text("profile_desc", Component.text("Description"))
                .initial(current)
                .maxLength(SettingsService.DESCRIPTION_MAX)
                .width(300)
                .multiline(TextDialogInput.MultilineOptions.create(4, 80))
                .build();
        List<ActionButton> actions = List.of(
                wideBtn("Save", "Save your description", (p, view) -> {
                    String typed = view.getText("profile_desc");
                    plugin.settings().setDescription(p.getUniqueId(), typed == null ? "" : typed);
                    Messages.send(plugin, p, "<green>Description updated.</green>");
                    plugin.logAction(p.getName() + " updated profile description");
                    back.accept(p);
                }),
                wideBtn("Clear", "Remove your description", p -> {
                    plugin.settings().setDescription(p.getUniqueId(), "");
                    Messages.send(plugin, p, "<yellow>Description cleared.</yellow>");
                    back.accept(p);
                })
        );
        show(player, Component.text("Profile Description"),
                List.of(textBody(Component.text("Shown on your stats and friend profile.", NamedTextColor.GRAY))),
                List.of(input), actions, 1, back, true);
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

    // --- GSit (optional) ---

    public static boolean isGsitEnabled() {
        var gsit = Bukkit.getPluginManager().getPlugin(GSIT_PLUGIN);
        return gsit != null && gsit.isEnabled();
    }

    public void openEnderChest(Player player) {
        player.closeDialog();
        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(player.getEnderChest()));
    }

    public void openSitLay(Player player) {
        if (!isGsitEnabled()) {
            Messages.send(plugin, player, "<red>GSit is not installed on this server.</red>");
            openMain(player);
            return;
        }
        List<ActionButton> actions = new ArrayList<>();
        actions.add(btn("Sit", "Run /sit", p -> runGsitCommand(p, "sit")));
        actions.add(btn("Crawl", "Run /crawl", p -> runGsitCommand(p, "crawl")));
        actions.add(btn("Lay", "Run /lay", p -> runGsitCommand(p, "lay")));
        actions.add(btn("Spin", "Run /spin", p -> runGsitCommand(p, "spin")));
        show(player, "Sit/lay", "GSit poses", actions, 1, this::openMain);
    }

    private void runGsitCommand(Player player, String command) {
        if (!isGsitEnabled()) {
            Messages.send(plugin, player, "<red>GSit is not installed on this server.</red>");
            openMain(player);
            return;
        }
        player.closeDialog();
        Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(command));
    }

    // --- Gamemode (OP) ---

    public void openGamemode(Player player) {
        if (!player.isOp()) {
            Messages.send(plugin, player, "<red>Only operators can change gamemode.</red>");
            openMain(player);
            return;
        }
        GameMode current = player.getGameMode();
        List<ActionButton> actions = new ArrayList<>();
        actions.add(gamemodeBtn("Survival", GameMode.SURVIVAL, current));
        actions.add(gamemodeBtn("Creative", GameMode.CREATIVE, current));
        actions.add(gamemodeBtn("Adventure", GameMode.ADVENTURE, current));
        actions.add(gamemodeBtn("Spectator", GameMode.SPECTATOR, current));
        show(player, "Gamemode", "Your gamemode: " + prettyGameMode(current), actions, 2, this::openMain);
    }

    private ActionButton gamemodeBtn(String label, GameMode mode, GameMode current) {
        String tooltip = mode == current
                ? "Currently " + label.toLowerCase(Locale.ROOT)
                : "Switch to " + label.toLowerCase(Locale.ROOT);
        return btn(label, tooltip, p -> setOwnGamemode(p, mode));
    }

    private void setOwnGamemode(Player player, GameMode mode) {
        if (!player.isOp()) {
            Messages.send(plugin, player, "<red>Only operators can change gamemode.</red>");
            openMain(player);
            return;
        }
        if (player.getGameMode() == mode) {
            openGamemode(player);
            return;
        }
        player.setGameMode(mode);
        Messages.send(plugin, player, "<green>Gamemode set to " + prettyGameMode(mode) + ".</green>");
        plugin.logAction(player.getName() + " set gamemode to " + mode.name());
        openGamemode(player);
    }

    private static String prettyGameMode(GameMode mode) {
        return switch (mode) {
            case SURVIVAL -> "Survival";
            case CREATIVE -> "Creative";
            case ADVENTURE -> "Adventure";
            case SPECTATOR -> "Spectator";
        };
    }

    // --- Settings ---

    public void openSettings(Player player) {
        List<ActionButton> actions = List.of(
                btn("Chat", "Who you see in chat", this::openSettingsChat),
                btn("Notifications", "Kill/death message filter", this::openSettingsNotifs),
                btn("Visuals", "Night vision & money nametag", this::openSettingsVisuals),
                btn("Privacy", "Profile fields & instant TPA", this::openSettingsPrivacy),
                btn("Description", "Edit your profile bio",
                        p -> openDescriptionEditor(p, this::openSettings)),
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
        List<DialogBody> bodies = (body == null || body.isBlank())
                ? List.of()
                : List.of(textBody(Component.text(body)));
        show(player, Component.text(title), bodies, List.of(), actions, columns, back, true);
    }

    private void show(Player player, Component title, List<DialogBody> bodies, List<ActionButton> actions, int columns,
                      Consumer<Player> back) {
        show(player, title, bodies, List.of(), actions, columns, back, true);
    }

    private void show(Player player, Component title, List<DialogBody> bodies, List<DialogInput> inputs,
                      List<ActionButton> actions, int columns, Consumer<Player> back, boolean footerExit) {
        int cols = fitColumns(actions.size(), columns);
        int width = Math.min(1024, Math.max(BTN_WIDTH, cols * BTN_WIDTH));
        if (!inputs.isEmpty()) {
            width = Math.max(width, BODY_WIDTH);
        }
        final int rowWidth = width;
        final int columnCount = cols;
        final List<ActionButton> fitted = columnCount == 1
                ? stretchAll(stretchLastRow(actions, columnCount), rowWidth)
                : stretchLastRow(actions, columnCount);
        final ActionButton exit;
        if (footerExit) {
            ActionButton raw = back == null
                    ? btn("Close", "Close menu", p -> p.closeDialog())
                    : btn("Back", "Go back", back);
            exit = withWidth(raw, rowWidth);
        } else {
            exit = null;
        }
        // Paper multiAction requires at least one action; fall back to notice when empty.
        if (fitted.isEmpty()) {
            ActionButton only = exit != null
                    ? exit
                    : withWidth(btn("Close", "Close menu", p -> p.closeDialog()), Math.max(rowWidth, BODY_WIDTH));
            Dialog notice = Dialog.create(factory -> factory.empty()
                    .base(DialogBase.builder(title)
                            .body(bodies)
                            .inputs(inputs)
                            .canCloseWithEscape(true)
                            .pause(false)
                            .afterAction(DialogBase.DialogAfterAction.NONE)
                            .build())
                    .type(DialogType.notice(only)));
            player.showDialog(notice);
            return;
        }
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .body(bodies)
                        .inputs(inputs)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build())
                .type(DialogType.multiAction(fitted, exit, columnCount)));
        player.showDialog(dialog);
    }

    private void showNotice(Player player, String title, String body, List<ActionButton> actions) {
        ActionButton ok = actions.isEmpty()
                ? btn("Close", "Close", p -> p.closeDialog())
                : actions.getFirst();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(title))
                        .body(List.of(textBody(Component.text(body))))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build())
                .type(DialogType.notice(withWidth(ok, BODY_WIDTH))));
        player.showDialog(dialog);
    }

    private ActionButton wideBtn(String label, String tooltip, Consumer<Player> click) {
        return btn(Component.text(label), tooltip, WIDE_BUTTON, (player, view) -> click.accept(player));
    }

    private ActionButton wideBtn(String label, String tooltip, BiConsumer<Player, DialogResponseView> click) {
        return btn(Component.text(label), tooltip, WIDE_BUTTON, click);
    }

    private ActionButton btn(String label, String tooltip, Consumer<Player> click) {
        return btn(Component.text(label), tooltip, click);
    }

    private ActionButton btn(String label, String tooltip, BiConsumer<Player, DialogResponseView> click) {
        return btn(Component.text(label), tooltip, BTN_WIDTH, click);
    }

    private ActionButton btn(Component label, String tooltip, Consumer<Player> click) {
        return btn(label, tooltip, BTN_WIDTH, (player, view) -> click.accept(player));
    }

    private static DialogBody textBody(Component text) {
        return DialogBody.plainMessage(text, BODY_WIDTH);
    }

    private static int fitColumns(int count, int preferred) {
        if (count <= 1) {
            return 1;
        }
        int pref = Math.max(1, preferred);
        if (count <= pref) {
            return count;
        }
        for (int cols = pref; cols >= 2; cols--) {
            int rem = count % cols;
            if (rem == 0 || rem == 1) {
                return cols;
            }
        }
        return 1;
    }

    private static List<ActionButton> stretchLastRow(List<ActionButton> actions, int columns) {
        int n = actions.size();
        if (n == 0 || columns <= 1 || n % columns != 1) {
            return actions;
        }
        ActionButton last = actions.get(n - 1);
        int full = Math.min(1024, last.width() * columns);
        if (full <= last.width()) {
            return actions;
        }
        List<ActionButton> out = new ArrayList<>(actions);
        out.set(n - 1, withWidth(last, full));
        return out;
    }

    private static List<ActionButton> stretchAll(List<ActionButton> actions, int width) {
        List<ActionButton> out = new ArrayList<>(actions.size());
        for (ActionButton button : actions) {
            out.add(withWidth(button, Math.max(button.width(), width)));
        }
        return out;
    }

    private static ActionButton withWidth(ActionButton button, int width) {
        if (button.width() == width) {
            return button;
        }
        return ActionButton.create(button.label(), button.tooltip(), width, button.action());
    }

    private static Component withHeadIcon(UUID uuid, String name) {
        return Component.object(ObjectContents.playerHead(uuid)).append(Component.text(" " + name));
    }

    private ActionButton btn(Component label, String tooltip, int width,
                             BiConsumer<Player, DialogResponseView> click) {
        return ActionButton.builder(label)
                .tooltip(Component.text(tooltip))
                .width(width)
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player player)) {
                        return;
                    }
                    Runnable run = () -> {
                        try {
                            click.accept(player, view);
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
