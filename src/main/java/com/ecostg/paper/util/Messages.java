package com.ecostg.paper.util;

import com.ecostg.paper.EcoSTGPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Messages() {
    }

    public static Component mm(String input) {
        return MM.deserialize(input == null ? "" : input);
    }

    public static String legacy(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    public static void send(EcoSTGPlugin plugin, CommandSender sender, String miniMessage) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        sender.sendMessage(mm(prefix + miniMessage));
    }

    /** Always compact: {@code $ 2.5K}, {@code $ 1.2M}, etc. */
    public static String money(EcoSTGPlugin plugin, double amount) {
        return compactMoney(plugin, amount);
    }

    public static String compactMoney(EcoSTGPlugin plugin, double amount) {
        String symbol = plugin.getConfig().getString("currency-symbol", "$");
        return symbol + " " + compactNumber(amount);
    }

    /**
     * Compact number with K / M / B / T suffixes.
     * Examples: 950 → {@code 950}, 2500 → {@code 2.5K}, 1_500_000 → {@code 1.5M}
     */
    public static String compactNumber(double amount) {
        double abs = Math.abs(amount);
        String sign = amount < 0 ? "-" : "";
        if (abs >= 1_000_000_000_000L) {
            return sign + trimCompact(abs / 1_000_000_000_000.0) + "T";
        }
        if (abs >= 1_000_000_000L) {
            return sign + trimCompact(abs / 1_000_000_000.0) + "B";
        }
        if (abs >= 1_000_000L) {
            return sign + trimCompact(abs / 1_000_000.0) + "M";
        }
        if (abs >= 1_000L) {
            return sign + trimCompact(abs / 1_000.0) + "K";
        }
        if (Math.abs(abs - Math.rint(abs)) < 0.001) {
            return sign + String.valueOf((long) Math.rint(abs));
        }
        return sign + String.format(java.util.Locale.US, "%.2f", abs);
    }

    private static String trimCompact(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(java.util.Locale.US, "%.1f", value);
    }
}
