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

    public static String money(EcoSTGPlugin plugin, double amount) {
        String symbol = plugin.getConfig().getString("currency-symbol", "$");
        String name = plugin.getConfig().getString("currency-name", "Dollars");
        return symbol + String.format(java.util.Locale.US, "%.2f", amount) + " " + name;
    }
}
