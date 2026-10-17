package com.ecostg.paper.util;

import com.ecostg.paper.EcoSTGPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Server-wide feature toggles from {@code config.yml} → {@code features.*}.
 * Defaults are {@code true} when a key is missing (existing configs keep everything on).
 */
public final class Features {

    private final EcoSTGPlugin plugin;

    public Features(EcoSTGPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean economy() {
        return flag("economy");
    }

    public boolean auction() {
        return economy() && flag("auction");
    }

    public boolean sell() {
        return economy() && flag("sell");
    }

    public boolean pay() {
        return economy() && flag("pay");
    }

    public boolean balance() {
        return economy() && flag("balance");
    }

    public boolean moneytop() {
        return economy() && flag("moneytop");
    }

    public boolean jobs() {
        return economy() && flag("jobs");
    }

    public boolean homes() {
        return flag("homes");
    }

    public boolean tpa() {
        return flag("tpa");
    }

    public boolean rtp() {
        return flag("rtp");
    }

    public boolean friends() {
        return flag("friends");
    }

    public boolean stats() {
        return flag("stats");
    }

    public boolean leaderboards() {
        return flag("leaderboards");
    }

    public boolean enderchest() {
        return flag("enderchest");
    }

    public boolean sitLay() {
        return flag("sit-lay");
    }

    public boolean settings() {
        return flag("settings");
    }

    public boolean combat() {
        return flag("combat");
    }

    public boolean gamemode() {
        return flag("gamemode");
    }

    /**
     * @return {@code true} if the feature is off (and the sender was notified)
     */
    public boolean denyIfDisabled(CommandSender sender, boolean enabled) {
        if (enabled) {
            return false;
        }
        String msg = plugin.getConfig().getString("messages.feature-disabled",
                "<red>That feature is disabled on this server.</red>");
        Messages.send(plugin, sender, msg);
        return true;
    }

    private boolean flag(String key) {
        return enabled(plugin.getConfig(), key);
    }

    /** Bootstrap / offline config helper (defaults to enabled). */
    public static boolean enabled(FileConfiguration config, String key) {
        if (config == null) {
            return true;
        }
        ConfigurationSection section = config.getConfigurationSection("features");
        if (section == null) {
            return true;
        }
        if (!section.getBoolean(key, true)) {
            return false;
        }
        if (isEconomyChild(key) && !section.getBoolean("economy", true)) {
            return false;
        }
        return true;
    }

    private static boolean isEconomyChild(String key) {
        return switch (key) {
            case "auction", "sell", "pay", "balance", "moneytop", "jobs" -> true;
            default -> false;
        };
    }
}
