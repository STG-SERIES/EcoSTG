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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class ChatInputListener implements Listener {

    public enum Mode {
        PAY_AMOUNT,
        AUCTION_PRICE
    }

    private record Pending(Mode mode, UUID target, BiConsumer<Player, String> handler) {
    }

    private final EcoSTGPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInputListener(EcoSTGPlugin plugin) {
        this.plugin = plugin;
    }

    public void await(Player player, Mode mode, UUID target, BiConsumer<Player, String> handler) {
        pending.put(player.getUniqueId(), new Pending(mode, target, handler));
    }

    public void clear(Player player) {
        pending.remove(player.getUniqueId());
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
        await(payer, Mode.PAY_AMOUNT, target, (player, text) -> {
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

    public void beginAuctionPrice(Player seller) {
        ItemStack hand = seller.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            Messages.send(plugin, seller, "<red>Hold the item you want to list.</red>");
            return;
        }
        ItemStack toList = hand.clone();
        await(seller, Mode.AUCTION_PRICE, null, (player, text) -> {
            if (text.equalsIgnoreCase("cancel")) {
                Messages.send(plugin, player, "<yellow>Listing cancelled.</yellow>");
                return;
            }
            double price;
            try {
                price = Double.parseDouble(text);
            } catch (NumberFormatException e) {
                Messages.send(plugin, player, plugin.getConfig().getString("messages.invalid-amount", "<red>Invalid amount.</red>"));
                return;
            }
            if (price <= 0) {
                Messages.send(plugin, player, plugin.getConfig().getString("messages.invalid-amount", "<red>Invalid amount.</red>"));
                return;
            }
            if (!plugin.economy().isEconomyEnabled(player.getUniqueId())) {
                Messages.send(plugin, player, plugin.getConfig().getString("messages.economy-disabled", ""));
                return;
            }
            ItemStack current = player.getInventory().getItemInMainHand();
            if (!current.isSimilar(toList) || current.getAmount() < toList.getAmount()) {
                Messages.send(plugin, player, "<red>Keep the same item in your hand to list it.</red>");
                return;
            }
            ItemStack listed = current.clone();
            current.setAmount(current.getAmount() - listed.getAmount());
            if (current.getAmount() <= 0) {
                player.getInventory().setItemInMainHand(null);
            } else {
                player.getInventory().setItemInMainHand(current);
            }
            boolean worker = plugin.jobs().isWorker(player.getUniqueId());
            long id = plugin.auctions().createListing(player, listed, price, worker);
            Messages.send(plugin, player, "<green>Listed item #" + id + " for " + Messages.money(plugin, price)
                    + (worker ? " <aqua>(By a worker)</aqua>" : "") + ".</green>");
            plugin.logAction(player.getName() + " listed AH#" + id + " for " + price
                    + (worker ? " [worker]" : ""));
            plugin.guis().openAuction(player, 0);
        });
        Messages.send(plugin, seller, "<yellow>Type the listing price (or 'cancel').</yellow>");
    }
}
