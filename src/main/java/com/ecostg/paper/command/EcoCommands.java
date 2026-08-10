package com.ecostg.paper.command;

import com.ecostg.paper.EcoSTGPlugin;
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
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "pay" -> requirePlayer(sender, p -> plugin.guis().openPay(p));
            case "shop" -> requirePlayer(sender, p -> plugin.guis().openAuction(p, 0));
            case "sell" -> requirePlayer(sender, p -> plugin.guis().openSell(p));
            case "moneytop" -> requirePlayer(sender, p -> plugin.guis().openMoneyTop(p));
            case "job" -> requirePlayer(sender, p -> plugin.guis().openJobs(p));
            case "jobsell" -> requirePlayer(sender, p -> plugin.guis().openJobSell(p));
            case "ecostg" -> handleEcoStg(sender, args);
            case "job-timer-reset" -> handleTimerReset(sender, args);
            default -> false;
        };
    }

    private boolean handleEcoStg(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ecostg.admin")) {
            Messages.send(plugin, sender, "<red>No permission.</red>");
            return true;
        }
        if (args.length < 3 || !args[0].equalsIgnoreCase("toggle")) {
            Messages.send(plugin, sender, "<yellow>Usage: /ecostg toggle <player> <on|off></yellow>");
            return true;
        }
        UUID uuid = plugin.economy().findUuidByName(args[1]);
        if (uuid == null) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]);
            if (offline.getUniqueId() == null || (!offline.hasPlayedBefore() && !offline.isOnline())) {
                Messages.send(plugin, sender, plugin.getConfig().getString("messages.player-not-found", ""));
                return true;
            }
            uuid = offline.getUniqueId();
            plugin.economy().ensurePlayer(uuid, offline.getName() == null ? args[1] : offline.getName());
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

    private boolean handleTimerReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ecostg.admin")) {
            Messages.send(plugin, sender, "<red>No permission.</red>");
            return true;
        }
        if (args.length < 1) {
            Messages.send(plugin, sender, "<yellow>Usage: /job-timer-reset <player></yellow>");
            return true;
        }
        UUID uuid = plugin.economy().findUuidByName(args[0]);
        if (uuid == null) {
            Messages.send(plugin, sender, plugin.getConfig().getString("messages.player-not-found", ""));
            return true;
        }
        plugin.jobs().resetCooldown(uuid);
        Messages.send(plugin, sender, "<green>Reset job cooldown for " + plugin.economy().getName(uuid) + ".</green>");
        plugin.logAction(sender.getName() + " reset job cooldown for " + plugin.economy().getName(uuid));
        return true;
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
        String name = command.getName().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (name.equals("ecostg")) {
            if (args.length == 1) {
                if ("toggle".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add("toggle");
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
                Bukkit.getOnlinePlayers().forEach(p -> {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        out.add(p.getName());
                    }
                });
            } else if (args.length == 3 && args[0].equalsIgnoreCase("toggle")) {
                for (String opt : List.of("on", "off")) {
                    if (opt.startsWith(args[2].toLowerCase(Locale.ROOT))) {
                        out.add(opt);
                    }
                }
            }
        } else if (name.equals("job-timer-reset") && args.length == 1) {
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            });
        }
        return out;
    }
}
