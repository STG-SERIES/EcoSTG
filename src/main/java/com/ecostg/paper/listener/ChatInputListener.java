package com.ecostg.paper.listener;

import com.ecostg.paper.EcoSTGPlugin;
import com.ecostg.paper.util.Messages;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class ChatInputListener implements Listener {

    public enum Mode {
        PAY_AMOUNT,
        AUCTION_PRICE
    }

    private record Pending(Mode mode, UUID target, ItemStack item, BiConsumer<Player, String> handler) {
    }

    private final EcoSTGPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInputListener(EcoSTGPlugin plugin) {
        this.plugin = plugin;
    }

    public void await(Player player, Mode mode, UUID target, ItemStack item, BiConsumer<Player, String> handler) {
        pending.put(player.getUniqueId(), new Pending(mode, target, item, handler));
    }

    public void clear(Player player) {
        Pending removed = pending.remove(player.getUniqueId());
        if (removed != null && removed.item() != null) {
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(removed.item());
            overflow.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Pending req = pending.remove(event.getPlayer().getUniqueId());
        if (req == null) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> req.handler().accept(player, text));
    }

    public void beginPayAmount(Player payer, UUID target) {
        await(payer, Mode.PAY_AMOUNT, target, null, (player, text) -> {
            if (text.equalsIgnoreCase("cancel")) {
                Messages.send(plugin, player, "<yellow>Payment cancelled.</yellow>");
                return;
            }
            double amount;
            try {
                amount = Double.parseDouble(text);
            } catch (NumberFormatException e) {
                Messages.send(plugin, player, plugin.getConfig().getString("messages.invalid-amount", "<red>Invalid amount.</red>"));
                return;
            }
            if (amount <= 0) {
                Messages.send(plugin, player, plugin.getConfig().getString("messages.invalid-amount", "<red>Invalid amount.</red>"));
                return;
            }
            if (!plugin.economy().isEconomyEnabled(player.getUniqueId())
                    || !plugin.economy().isEconomyEnabled(target)) {
                Messages.send(plugin, player, plugin.getConfig().getString("messages.economy-disabled", ""));
                return;
            }
            if (!plugin.economy().transfer(player.getUniqueId(), target, amount)) {
                Messages.send(plugin, player, plugin.getConfig().getString("messages.insufficient-funds", ""));
                return;
            }
            String targetName = plugin.economy().getName(target);
            Messages.send(plugin, player, "<green>Paid " + Messages.money(plugin, amount) + " to " + targetName + ".</green>");
            Player online = Bukkit.getPlayer(target);
            if (online != null) {
                Messages.send(plugin, online, "<green>You received " + Messages.money(plugin, amount) + " from " + player.getName() + ".</green>");
            }
            plugin.logAction(player.getName() + " paid " + amount + " to " + targetName);
        });
        Messages.send(plugin, payer, "<yellow>Type the amount to pay (or 'cancel').</yellow>");
    }

    public void beginSellListing(Player seller, ItemStack toList) {
        await(seller, Mode.AUCTION_PRICE, null, toList, (player, text) -> {
            if (text.equalsIgnoreCase("cancel")) {
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(toList);
                overflow.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
                Messages.send(plugin, player, "<yellow>Listing cancelled. Item returned.</yellow>");
                return;
            }
            double price;
            try {
                price = Double.parseDouble(text.replace(",", "").replace("$", "").trim());
            } catch (NumberFormatException e) {
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(toList);
                overflow.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
                Messages.send(plugin, player, plugin.getConfig().getString("messages.invalid-amount", "<red>Invalid amount.</red>"));
                Messages.send(plugin, player, "<gray>Item returned. Use /sell to try again.</gray>");
                return;
            }
            if (price <= 0) {
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(toList);
                overflow.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
                Messages.send(plugin, player, plugin.getConfig().getString("messages.invalid-amount", "<red>Invalid amount.</red>"));
                return;
            }
            if (!plugin.economy().isEconomyEnabled(player.getUniqueId())) {
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(toList);
                overflow.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
                Messages.send(plugin, player, plugin.getConfig().getString("messages.economy-disabled", ""));
                return;
            }
            // Regular /sell never gets the worker badge — only /jobsell does
            long id = plugin.auctions().createListing(player, toList, price, false);
            Messages.send(plugin, player, "<green>Listed on AH #" + id + " for " + Messages.money(plugin, price) + ".</green>");
            plugin.logAction(player.getName() + " listed AH#" + id + " for custom price " + price);
            plugin.guis().openAuction(player, 0);
        });
        double suggest = plugin.worth().valueOf(toList);
        if (suggest > 0) {
            Messages.send(plugin, seller, "<yellow>Type your listing price (or 'cancel'). Suggested: "
                    + Messages.money(plugin, suggest) + "</yellow>");
        } else {
            Messages.send(plugin, seller, "<yellow>Type your custom listing price (or 'cancel').</yellow>");
        }
    }

    public void beginJobSellListing(Player seller, ItemStack toList) {
        double discount = plugin.getConfig().getDouble("jobs.worker-listing-discount-percent", 5.0);
        String discountText = Math.abs(discount - Math.rint(discount)) < 0.001
                ? String.valueOf((int) Math.rint(discount))
                : String.format(java.util.Locale.US, "%.1f", discount);
        await(seller, Mode.AUCTION_PRICE, null, toList, (player, text) -> {
            if (text.equalsIgnoreCase("cancel")) {
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(toList);
                overflow.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
                Messages.send(plugin, player, "<yellow>Job sell cancelled. Items returned. Deadline not refreshed.</yellow>");
                return;
            }
            double price;
            try {
                price = Double.parseDouble(text.replace(",", "").replace("$", "").trim());
            } catch (NumberFormatException e) {
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(toList);
                overflow.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
                Messages.send(plugin, player, plugin.getConfig().getString("messages.invalid-amount", "<red>Invalid amount.</red>"));
                Messages.send(plugin, player, "<gray>Items returned. Use /jobsell to try again.</gray>");
                return;
            }
            if (price <= 0) {
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(toList);
                overflow.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
                Messages.send(plugin, player, plugin.getConfig().getString("messages.invalid-amount", "<red>Invalid amount.</red>"));
                return;
            }
            if (!plugin.economy().isEconomyEnabled(player.getUniqueId())) {
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(toList);
                overflow.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
                Messages.send(plugin, player, plugin.getConfig().getString("messages.economy-disabled", ""));
                return;
            }
            if (!plugin.jobs().isWorker(player.getUniqueId())) {
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(toList);
                overflow.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
                Messages.send(plugin, player, "<red>You no longer have an active job.</red>");
                return;
            }
            long id = plugin.auctions().createListing(player, toList, price, true);
            plugin.jobs().refreshDeadline(player);
            Messages.send(plugin, player, "<green>Job items listed on AH #" + id + " for "
                    + Messages.money(plugin, price) + " <aqua>(By a worker — " + discountText + "% off)</aqua>.</green>");
            Messages.send(plugin, player, "<green>Delivery complete! Deadline refreshed.</green>");
            plugin.logAction(player.getName() + " jobsell-listed AH#" + id + " for " + price + " [worker]");
            plugin.guis().openAuction(player, 0);
        });
        double suggest = plugin.worth().valueOf(toList);
        Messages.send(plugin, seller, "<yellow>Type the AH price for your worker listing (or 'cancel').</yellow>");
        Messages.send(plugin, seller, "<aqua>Badge: By a worker — " + discountText + "% discount for buyers.</aqua>");
        if (suggest > 0) {
            Messages.send(plugin, seller, "<gray>Suggested: " + Messages.money(plugin, suggest) + "</gray>");
        }
    }
}
