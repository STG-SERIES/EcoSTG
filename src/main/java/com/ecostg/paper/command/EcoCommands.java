package com.ecostg.paper.command;

import com.ecostg.paper.EcoSTGPlugin;
import com.ecostg.paper.job.JobDefinition;
import com.ecostg.paper.job.JobService;
import com.ecostg.paper.menu.FriendService;
import com.ecostg.paper.menu.HomeService;
import com.ecostg.paper.menu.StatsService;
import com.ecostg.paper.menu.TpaService;
import com.ecostg.paper.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class EcoCommands implements CommandExecutor, TabCompleter {

    private final EcoSTGPlugin plugin;

    public EcoCommands(EcoSTGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(sender, label, args);
    }

    public boolean execute(CommandSender sender, String label, String[] args) {
        String name = resolveName(label);
        return switch (name) {
            case "pay" -> requirePlayer(sender, p -> handlePay(p, args));
            case "shop", "ah", "auction" -> requirePlayer(sender, p -> plugin.guis().openAuction(p, 0));
            case "sell" -> requirePlayer(sender, p -> plugin.guis().openSell(p));
            case "menu", "ecostgmenu" -> requirePlayer(sender, p -> plugin.menus().openMain(p));
            case "tpa" -> requirePlayer(sender, p -> handleTpa(p, args, TpaService.Type.TPA));
            case "tpahere", "tphere" -> requirePlayer(sender, p -> handleTpa(p, args, TpaService.Type.TPA_HERE));
            case "tpaccept", "tpyes" -> requirePlayer(sender, p -> plugin.tpa().accept(p));
            case "tpdeny", "tpno" -> requirePlayer(sender, p -> plugin.tpa().deny(p));
            case "rtp" -> requirePlayer(sender, p -> plugin.rtp().rtp(p));
            case "rtpq", "rtpqueue" -> requirePlayer(sender, p -> plugin.rtp().rtpQueue(p));
            case "stats" -> requirePlayer(sender, p -> handleStats(p, args));
            case "home", "homes" -> requirePlayer(sender, p -> handleHome(p, args));
            case "sethome" -> requirePlayer(sender, p -> handleSetHome(p, args));
            case "delhome" -> requirePlayer(sender, p -> handleDelHome(p, args));
            case "friend", "friends" -> requirePlayer(sender, p -> handleFriend(p, args));
            case "settings" -> requirePlayer(sender, p -> plugin.menus().openSettings(p));
            case "leaderboard", "leaderboards", "lb" -> requirePlayer(sender, p -> handleLeaderboard(p, args));
            case "bal", "balance", "money" -> requirePlayer(sender, p -> handleBalance(p, args));
            case "moneytop" -> requirePlayer(sender, p -> plugin.guis().openMoneyTop(p));
            case "job" -> requirePlayer(sender, p -> plugin.guis().openJobs(p));
            case "jobsell" -> requirePlayer(sender, p -> plugin.guis().openJobSell(p));
            case "jobinfo" -> requirePlayer(sender, this::handleJobInfo);
            case "activejobs" -> requirePlayer(sender, this::handleActiveJobs);
            case "ecostg" -> handleEcoStg(sender, args);
            case "job-timer-reset" -> handleTimerReset(sender, args);
            case "ecoset" -> handleEcoSet(sender, args);
            case "ecogive" -> handleEcoGive(sender, args);
            case "jobcancel" -> handleJobCancel(sender, args);
            default -> false;
        };
    }

    private static String resolveName(String label) {
        return label.toLowerCase(Locale.ROOT);
    }

    private void handleJobInfo(Player player) {
        plugin.economy().ensurePlayer(player);
        JobService.PlayerJob job = plugin.jobs().getPlayerJob(player.getUniqueId());
        if (!job.hasJob()) {
            Messages.send(plugin, player, "<yellow>You do not have an active job.</yellow>");
            if (plugin.jobs().isOnCooldown(player.getUniqueId())) {
                long days = java.util.concurrent.TimeUnit.MILLISECONDS
                        .toDays(plugin.jobs().cooldownRemainingMs(player.getUniqueId())) + 1;
                Messages.send(plugin, player, "<gray>Fired cooldown: about <white>" + days + "</white> day(s) left.</gray>");
            }
            return;
        }
        JobDefinition def = plugin.jobs().get(job.jobId());
        String jobName = def == null ? job.jobId() : def.displayName();
        long daysLeft = plugin.jobs().daysUntilDeadline(job);
        Messages.send(plugin, player, "<green>Job:</green> <white>" + jobName + "</white>");
        if (def != null) {
            Messages.send(plugin, player, "<green>Delivery:</green> <white>"
                    + def.requiredAmount() + "x " + GuiPretty.pretty(def.requiredMaterial()) + "</white>");
        }
        Messages.send(plugin, player, "<green>Days until delivery due:</green> <white>" + daysLeft + "</white>");
        Messages.send(plugin, player, "<green>Deadline world day:</green> <white>" + job.deadlineDay()
                + "</white> <gray>(current: " + plugin.jobs().currentWorldDay() + ")</gray>");
    }

    private void handlePay(Player player, String[] args) {
        if (args.length == 0) {
            plugin.menus().openPayPlayers(player);
            return;
        }
        Player target = findOnline(args[0]);
        if (target == null) {
            Messages.send(plugin, player, plugin.getConfig().getString("messages.player-not-found", ""));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            Messages.send(plugin, player, "<red>You cannot pay yourself.</red>");
            return;
        }
        if (args.length == 1) {
            plugin.menus().openPayAmounts(player, target.getUniqueId(), target.getName());
            return;
        }
        Double amount = parseAmount(args[1]);
        if (amount == null || amount <= 0) {
            Messages.send(plugin, player, plugin.getConfig().getString("messages.invalid-amount", ""));
            return;
        }
        plugin.menus().pay(player, target.getUniqueId(), target.getName(), amount);
    }

    private void handleTpa(Player player, String[] args, TpaService.Type type) {
        if (args.length == 0) {
            plugin.menus().openTeleportTargets(player, type);
            return;
        }
        Player target = findOnline(args[0]);
        if (target == null) {
            Messages.send(plugin, player, plugin.getConfig().getString("messages.player-not-found", ""));
            return;
        }
        plugin.tpa().request(player, target, type);
    }

    private void handleStats(Player player, String[] args) {
        if (args.length == 0) {
            plugin.menus().openStatsView(player, player.getUniqueId(), player.getName());
            return;
        }
        UUID uuid = resolvePlayer(args[0]);
        if (uuid == null) {
            Messages.send(plugin, player, plugin.getConfig().getString("messages.player-not-found", ""));
            return;
        }
        plugin.menus().openStatsView(player, uuid, plugin.economy().getName(uuid));
    }

    private void handleHome(Player player, String[] args) {
        if (args.length == 0) {
            plugin.menus().openHomes(player);
            return;
        }
        String name = args[0];
        if (plugin.homes().teleport(player, name)) {
            Messages.send(plugin, player, "<green>Teleported to home '" + name.toLowerCase(Locale.ROOT) + "'.</green>");
        } else {
            Messages.send(plugin, player, "<red>No home named '" + name + "'.</red>");
        }
    }

    private void handleSetHome(Player player, String[] args) {
        List<HomeService.Home> homes = plugin.homes().list(player.getUniqueId());
        String name = args.length >= 1 ? args[0] : "home" + (homes.size() + 1);
        if (plugin.homes().setHome(player, name)) {
            Messages.send(plugin, player, "<green>Home '" + name.toLowerCase(Locale.ROOT) + "' set.</green>");
        } else {
            Messages.send(plugin, player, "<red>Could not set home (max " + plugin.homes().maxHomes()
                    + ", name letters/numbers/underscore up to 16).</red>");
        }
    }

    private void handleDelHome(Player player, String[] args) {
        if (args.length < 1) {
            Messages.send(plugin, player, "<yellow>Usage: /delhome <name></yellow>");
            return;
        }
        if (plugin.homes().deleteHome(player.getUniqueId(), args[0])) {
            Messages.send(plugin, player, "<yellow>Deleted home '" + args[0] + "'.</yellow>");
        } else {
            Messages.send(plugin, player, "<red>No home named '" + args[0] + "'.</red>");
        }
    }

    private void handleFriend(Player player, String[] args) {
        if (args.length == 0) {
            plugin.menus().openFriends(player);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            sendFriendList(player);
            return;
        }
        if (args.length < 2 || !(sub.equals("add") || sub.equals("follow")
                || sub.equals("remove") || sub.equals("unfollow") || sub.equals("del"))) {
            Messages.send(plugin, player, "<yellow>Usage: /friend [add|remove|list] [player]</yellow>");
            return;
        }
        if (sub.equals("add") || sub.equals("follow")) {
            Player target = findOnline(args[1]);
            if (target == null) {
                Messages.send(plugin, player, plugin.getConfig().getString("messages.player-not-found", ""));
                return;
            }
            plugin.economy().ensurePlayer(target);
            FriendService.FollowResult result = plugin.friends().follow(player.getUniqueId(), target.getUniqueId());
            if (result == FriendService.FollowResult.INVALID) {
                Messages.send(plugin, player, "<red>You cannot follow yourself.</red>");
            } else if (result == FriendService.FollowResult.ALREADY_FOLLOWING) {
                Messages.send(plugin, player, "<yellow>You already follow " + target.getName() + ".</yellow>");
            }
            return;
        }
        UUID uuid = resolvePlayer(args[1]);
        if (uuid == null) {
            Messages.send(plugin, player, plugin.getConfig().getString("messages.player-not-found", ""));
            return;
        }
        if (!plugin.friends().unfollow(player.getUniqueId(), uuid)) {
            Messages.send(plugin, player, "<red>You are not following " + plugin.economy().getName(uuid) + ".</red>");
        }
    }

    private void sendFriendList(Player player) {
        UUID id = player.getUniqueId();
        var friends = plugin.friends().listFriends(id);
        var following = plugin.friends().listPendingFollowing(id);
        var followers = plugin.friends().listPendingFollowers(id);
        if (friends.isEmpty() && following.isEmpty() && followers.isEmpty()) {
            Messages.send(plugin, player, "<yellow>No follows yet. /friend add <player> to follow someone.</yellow>");
            return;
        }
        Messages.send(plugin, player, "<green>Friends:</green> <white>" + joinFriendNames(friends) + "</white>");
        Messages.send(plugin, player, "<gray>Following:</gray> <white>" + joinFriendNames(following) + "</white>");
        Messages.send(plugin, player, "<gray>Followers:</gray> <white>" + joinFriendNames(followers) + "</white>");
    }

    private static String joinFriendNames(List<FriendService.FriendEntry> entries) {
        if (entries.isEmpty()) {
            return "none";
        }
        return String.join(", ", entries.stream().map(FriendService.FriendEntry::name).toList());
    }

    private void handleLeaderboard(Player player, String[] args) {
        if (args.length == 0) {
            plugin.menus().openLeaderboards(player);
            return;
        }
        StatsService.LeaderboardType type = parseBoard(args[0]);
        if (type == null) {
            Messages.send(plugin, player, "<yellow>Usage: /leaderboard [money|playtime|kills|deaths|placed|broken|mobs]</yellow>");
            plugin.menus().openLeaderboards(player);
            return;
        }
        plugin.menus().openLeaderboard(player, type);
    }

    private static StatsService.LeaderboardType parseBoard(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "money", "bal", "baltop" -> StatsService.LeaderboardType.MONEY;
            case "playtime", "time" -> StatsService.LeaderboardType.PLAYTIME;
            case "kills", "kill" -> StatsService.LeaderboardType.KILLS;
            case "deaths", "death" -> StatsService.LeaderboardType.DEATHS;
            case "placed", "place", "blocksplaced" -> StatsService.LeaderboardType.BLOCKS_PLACED;
            case "broken", "break", "blocks", "blocksbroken" -> StatsService.LeaderboardType.BLOCKS_BROKEN;
            case "mobs", "mob", "mobkills" -> StatsService.LeaderboardType.MOBS_KILLED;
            default -> null;
        };
    }

    private void handleBalance(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        if (args.length >= 1) {
            UUID resolved = resolvePlayer(args[0]);
            if (resolved == null) {
                Messages.send(plugin, player, plugin.getConfig().getString("messages.player-not-found", ""));
                return;
            }
            uuid = resolved;
            name = plugin.economy().getName(resolved);
            if (!uuid.equals(player.getUniqueId())
                    && !plugin.settings().get(uuid).showMoney()
                    && !player.hasPermission("ecostg.admin")) {
                Messages.send(plugin, player, "<yellow>" + name + "'s money is hidden.</yellow>");
                return;
            }
        }
        Messages.send(plugin, player, "<green>" + name + ":</green> <white>"
                + Messages.money(plugin, plugin.economy().getBalance(uuid)) + "</white>");
    }

    private Player findOnline(String name) {
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) {
            return exact;
        }
        Player match = null;
        String lower = name.toLowerCase(Locale.ROOT);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(name)) {
                return online;
            }
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                if (match != null) {
                    return null;
                }
                match = online;
            }
        }
        return match;
    }

    private void handleActiveJobs(Player player) {
        if (!player.hasPermission("ecostg.admin")) {
            Messages.send(plugin, player, "<red>No permission.</red>");
            return;
        }
        plugin.guis().openActiveJobs(player);
    }

    private boolean handleEcoSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ecostg.admin")) {
            Messages.send(plugin, sender, "<red>No permission.</red>");
            return true;
        }
        if (args.length < 2) {
            Messages.send(plugin, sender, "<yellow>Usage: /ecoset <player> <amount></yellow>");
            return true;
        }
        UUID uuid = resolvePlayer(args[0]);
        if (uuid == null) {
            Messages.send(plugin, sender, plugin.getConfig().getString("messages.player-not-found", ""));
            return true;
        }
        Double amount = parseAmount(args[1]);
        if (amount == null || amount < 0) {
            Messages.send(plugin, sender, plugin.getConfig().getString("messages.invalid-amount", ""));
            return true;
        }
        plugin.economy().setBalance(uuid, amount);
        Messages.send(plugin, sender, "<green>Set " + plugin.economy().getName(uuid)
                + "'s balance to " + Messages.money(plugin, amount) + ".</green>");
        plugin.logAction(sender.getName() + " set balance of " + plugin.economy().getName(uuid) + " to " + amount);
        return true;
    }

    private boolean handleEcoGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ecostg.admin")) {
            Messages.send(plugin, sender, "<red>No permission.</red>");
            return true;
        }
        if (args.length < 2) {
            Messages.send(plugin, sender, "<yellow>Usage: /ecogive <player> <amount></yellow>");
            return true;
        }
        UUID uuid = resolvePlayer(args[0]);
        if (uuid == null) {
            Messages.send(plugin, sender, plugin.getConfig().getString("messages.player-not-found", ""));
            return true;
        }
        Double amount = parseAmount(args[1]);
        if (amount == null || amount <= 0) {
            Messages.send(plugin, sender, plugin.getConfig().getString("messages.invalid-amount", ""));
            return true;
        }
        plugin.economy().deposit(uuid, amount);
        Messages.send(plugin, sender, "<green>Gave " + Messages.money(plugin, amount)
                + " to " + plugin.economy().getName(uuid) + ".</green>");
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            Messages.send(plugin, online, "<green>You received " + Messages.money(plugin, amount) + " from an admin.</green>");
        }
        plugin.logAction(sender.getName() + " gave " + amount + " to " + plugin.economy().getName(uuid));
        return true;
    }

    private boolean handleJobCancel(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ecostg.admin")) {
            Messages.send(plugin, sender, "<red>No permission.</red>");
            return true;
        }
        if (args.length < 1) {
            Messages.send(plugin, sender, "<yellow>Usage: /jobcancel <player></yellow>");
            return true;
        }
        UUID uuid = resolvePlayer(args[0]);
        if (uuid == null) {
            Messages.send(plugin, sender, plugin.getConfig().getString("messages.player-not-found", ""));
            return true;
        }
        if (!plugin.jobs().cancelJob(uuid)) {
            Messages.send(plugin, sender, "<red>That player has no active job.</red>");
            return true;
        }
        Messages.send(plugin, sender, "<green>Cancelled job for " + plugin.economy().getName(uuid) + ".</green>");
        plugin.logAction(sender.getName() + " cancelled job for " + plugin.economy().getName(uuid));
        return true;
    }

    private boolean handleEcoStg(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("toggle")) {
            if (!sender.hasPermission("ecostg.admin")) {
                Messages.send(plugin, sender, "<red>No permission.</red>");
                return true;
            }
            if (args.length < 3) {
                Messages.send(plugin, sender, "<yellow>Usage: /ecostg toggle <player> <on|off></yellow>");
                return true;
            }
            UUID uuid = resolvePlayer(args[1]);
            if (uuid == null) {
                Messages.send(plugin, sender, plugin.getConfig().getString("messages.player-not-found", ""));
                return true;
            }
            boolean enable = args[2].equalsIgnoreCase("on");
            if (!enable && !args[2].equalsIgnoreCase("off")) {
                Messages.send(plugin, sender, "<yellow>Use on or off.</yellow>");
                return true;
            }
            plugin.economy().setEconomyEnabled(uuid, enable);
            Messages.send(plugin, sender, "<green>Economy for " + plugin.economy().getName(uuid)
                    + " is now " + (enable ? "ON" : "OFF") + ".</green>");
            plugin.logAction(sender.getName() + " set economy " + (enable ? "ON" : "OFF")
                    + " for " + plugin.economy().getName(uuid));
            return true;
        }
        if (args[0].equalsIgnoreCase("mainletters")) {
            if (!sender.hasPermission("ecostg.admin") && !sender.isOp()) {
                Messages.send(plugin, sender, "<red>No permission.</red>");
                return true;
            }
            if (args.length < 3 || !args[1].equalsIgnoreCase("change")) {
                Messages.send(plugin, sender, "<yellow>Usage: /ecostg mainletters change <name></yellow>");
                return true;
            }
            StringBuilder name = new StringBuilder(args[2]);
            for (int i = 3; i < args.length; i++) {
                name.append(' ').append(args[i]);
            }
            String letters = name.toString().trim();
            if (letters.isEmpty() || letters.length() > 32) {
                Messages.send(plugin, sender, "<red>Name must be 1-32 characters.</red>");
                return true;
            }
            plugin.menus().setMainLetters(letters);
            Messages.send(plugin, sender, "<green>Main letters set to <white>" + letters
                    + "</white>. Menus/Server Links updated. Pause button label needs a server restart.</green>");
            return true;
        }
        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        double workerListing = plugin.getConfig().getDouble("jobs.worker-listing-discount-percent", 5.0);
        double workerBuyer = plugin.getConfig().getDouble("jobs.ah-buyer-discount-percent", 8.0);
        int deadlineDays = plugin.getConfig().getInt("jobs.deadline-ingame-days", 4);
        int fireCooldown = plugin.getConfig().getInt("jobs.fire-cooldown-real-days", 30);
        String start = Messages.money(plugin, plugin.getConfig().getDouble("starting-balance", 500.0));

        Messages.send(plugin, sender, "<gold><bold>EcoSTG Paper — Help</bold></gold>");
        Messages.send(plugin, sender, "<gray>Currency: Dollars · New players start with " + start + "</gray>");
        Messages.send(plugin, sender, "");
        Messages.send(plugin, sender, "<yellow>Menu</yellow>");
        Messages.send(plugin, sender, "<white>/menu</white> <gray>— open hub (also ESC pause button)</gray>");
        Messages.send(plugin, sender, "<white>/settings</white> <gray>— privacy, chat, visuals</gray>");
        Messages.send(plugin, sender, "");
        Messages.send(plugin, sender, "<yellow>Teleport</yellow>");
        Messages.send(plugin, sender, "<white>/tpa <player></white> <gray>·</gray> <white>/tpahere <player></white>");
        Messages.send(plugin, sender, "<white>/tpaccept</white> <gray>·</gray> <white>/tpdeny</white>");
        Messages.send(plugin, sender, "<white>/rtp</white> <gray>— random teleport</gray> <white>/rtpq</white> <gray>— random player</gray>");
        Messages.send(plugin, sender, "<white>/home [name]</white> <gray>·</gray> <white>/sethome [name]</white> <gray>·</gray> <white>/delhome <name></white>");
        Messages.send(plugin, sender, "");
        Messages.send(plugin, sender, "<yellow>Players</yellow>");
        Messages.send(plugin, sender, "<white>/stats [player]</white>");
        Messages.send(plugin, sender, "<white>/friend add|remove|list</white> <gray>— follow; friends when both follow</gray>");
        Messages.send(plugin, sender, "<white>/leaderboard [money|playtime|kills|...]</white> <gray>(/lb)</gray>");
        Messages.send(plugin, sender, "");
        Messages.send(plugin, sender, "<yellow>Economy</yellow>");
        Messages.send(plugin, sender, "<white>/pay [player] [amount]</white> <gray>— GUI if you omit args</gray>");
        Messages.send(plugin, sender, "<white>/bal [player]</white> <gray>·</gray> <white>/shop</white> <gray>(/ah) ·</gray> <white>/sell</white> <gray>·</gray> <white>/moneytop</white>");
        Messages.send(plugin, sender, "");
        Messages.send(plugin, sender, "<yellow>Jobs</yellow>");
        Messages.send(plugin, sender, "<white>/job</white> <gray>— choose a job (one at a time)</gray>");
        Messages.send(plugin, sender, "<white>/jobsell</white> <gray>— sell required job items on the AH</gray>");
        Messages.send(plugin, sender, "<gray>  Those listings get <aqua>By a worker</aqua> and "
                + formatPct(workerListing) + "% off for buyers.</gray>");
        Messages.send(plugin, sender, "<gray>  Workers also get " + formatPct(workerBuyer)
                + "% off when buying from the AH.</gray>");
        Messages.send(plugin, sender, "<white>/jobinfo</white> <gray>— your job status & days until delivery is due</gray>");
        Messages.send(plugin, sender, "<gray>Deliver every <white>" + deadlineDays
                + "</white> in-game days or you are fired (" + fireCooldown + " real days cooldown).</gray>");
        if (sender.hasPermission("ecostg.admin")) {
            Messages.send(plugin, sender, "");
            Messages.send(plugin, sender, "<red>Admin</red>");
            Messages.send(plugin, sender, "<white>/ecostg toggle <player> <on|off></white>");
            Messages.send(plugin, sender, "<white>/ecostg mainletters change <name></white>");
            Messages.send(plugin, sender, "<white>/ecoset</white> <gray>·</gray> <white>/ecogive</white> <gray>·</gray> <white>/jobcancel</white>");
            Messages.send(plugin, sender, "<white>/activejobs</white> <gray>·</gray> <white>/job-timer-reset</white>");
        }
        Messages.send(plugin, sender, "");
        Messages.send(plugin, sender, "<dark_gray>Tip: /ecostg help</dark_gray>");
    }

    private static String formatPct(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    private boolean handleTimerReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ecostg.admin")) {
            Messages.send(plugin, sender, "<red>No permission.</red>");
            return true;
        }
        if (args.length < 1) {
            Messages.send(plugin, sender, "<yellow>Usage: /job-timer-reset <player></yellow>");
            return true;
        }
        UUID uuid = resolvePlayer(args[0]);
        if (uuid == null) {
            Messages.send(plugin, sender, plugin.getConfig().getString("messages.player-not-found", ""));
            return true;
        }
        plugin.jobs().resetCooldown(uuid);
        Messages.send(plugin, sender, "<green>Reset job cooldown for " + plugin.economy().getName(uuid) + ".</green>");
        plugin.logAction(sender.getName() + " reset job cooldown for " + plugin.economy().getName(uuid));
        return true;
    }

    private UUID resolvePlayer(String name) {
        UUID uuid = plugin.economy().findUuidByName(name);
        if (uuid != null) {
            return uuid;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (!offline.hasPlayedBefore() && !offline.isOnline()) {
            return null;
        }
        uuid = offline.getUniqueId();
        plugin.economy().ensurePlayer(uuid, offline.getName() == null ? name : offline.getName());
        return uuid;
    }

    private static Double parseAmount(String raw) {
        try {
            return Double.parseDouble(raw.replace(",", "").replace("$", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean requirePlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (!(sender instanceof Player player)) {
            Messages.send(plugin, sender, "<red>Players only.</red>");
            return true;
        }
        action.accept(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return suggest(sender, alias, args);
    }

    public List<String> suggest(CommandSender sender, String alias, String[] args) {
        String name = resolveName(alias);
        List<String> out = new ArrayList<>();
        if (name.equals("ecostg")) {
            if (args.length == 1) {
                for (String opt : List.of("help", "toggle", "mainletters")) {
                    if (opt.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                        if ((opt.equals("toggle") || opt.equals("mainletters"))
                                && !sender.hasPermission("ecostg.admin") && !sender.isOp()) {
                            continue;
                        }
                        out.add(opt);
                    }
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("toggle") && sender.hasPermission("ecostg.admin")) {
                completePlayers(out, args[1]);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("mainletters")
                    && (sender.hasPermission("ecostg.admin") || sender.isOp())) {
                if ("change".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add("change");
                }
            } else if (args.length == 3 && args[0].equalsIgnoreCase("toggle") && sender.hasPermission("ecostg.admin")) {
                for (String opt : List.of("on", "off")) {
                    if (opt.startsWith(args[2].toLowerCase(Locale.ROOT))) {
                        out.add(opt);
                    }
                }
            }
        } else if ((name.equals("job-timer-reset") || name.equals("jobcancel")
                || name.equals("ecoset") || name.equals("ecogive")
                || name.equals("tpa") || name.equals("tpahere")
                || name.equals("pay") || name.equals("stats")
                || name.equals("bal") || name.equals("balance") || name.equals("money"))
                && args.length == 1) {
            completePlayers(out, args[0]);
        } else if ((name.equals("home") || name.equals("delhome")) && args.length == 1
                && sender instanceof Player player) {
            completeHomes(player, out, args[0]);
        } else if ((name.equals("friend") || name.equals("friends"))) {
            if (args.length == 1) {
                for (String opt : List.of("add", "follow", "remove", "unfollow", "list")) {
                    if (opt.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                        out.add(opt);
                    }
                }
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("follow"))) {
                completePlayers(out, args[1]);
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("remove")
                    || args[0].equalsIgnoreCase("unfollow") || args[0].equalsIgnoreCase("del"))
                    && sender instanceof Player player) {
                String p = args[1].toLowerCase(Locale.ROOT);
                for (var friend : plugin.friends().listFollowing(player.getUniqueId())) {
                    if (friend.name().toLowerCase(Locale.ROOT).startsWith(p)) {
                        out.add(friend.name());
                    }
                }
            }
        } else if ((name.equals("leaderboard") || name.equals("leaderboards") || name.equals("lb"))
                && args.length == 1) {
            for (String opt : List.of("money", "playtime", "kills", "deaths", "placed", "broken", "mobs")) {
                if (opt.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(opt);
                }
            }
        }
        return out;
    }

    private void completeHomes(Player player, List<String> out, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        for (HomeService.Home home : plugin.homes().list(player.getUniqueId())) {
            if (home.name().startsWith(p)) {
                out.add(home.name());
            }
        }
    }

    private void completePlayers(List<String> out, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        Bukkit.getOnlinePlayers().forEach(player -> {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(player.getName());
            }
        });
    }

    /** Tiny helper to avoid importing GuiManager in every message. */
    private static final class GuiPretty {
        static String pretty(org.bukkit.Material material) {
            return com.ecostg.paper.gui.GuiManager.pretty(material);
        }
    }
}
