package com.ecostg.paper.gui;

import com.ecostg.paper.EcoSTGPlugin;
import com.ecostg.paper.economy.AuctionService;
import com.ecostg.paper.job.JobDefinition;
import com.ecostg.paper.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.UUID;

public final class GuiListener implements Listener {

    private final EcoSTGPlugin plugin;

    public GuiListener(EcoSTGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        GuiSession session = plugin.guis().session(player);
        if (session == null) {
            return;
        }

        GuiType type = session.type();
        if (type == GuiType.SELL) {
            handleSellClick(event, player, session);
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        int slot = event.getRawSlot();
        switch (type) {
            case PAY_PLAYERS -> {
                UUID target = session.get("slot:" + slot);
                if (target != null) {
                    player.closeInventory();
                    plugin.chatInput().beginPayAmount(player, target);
                }
            }
            case AUCTION -> handleAuctionClick(player, session, slot);
            case AUCTION_CONFIRM -> handleConfirm(player, session, slot);
            case MONEYTOP -> {
            }
            case JOBS -> {
                String jobId = session.get("slot:" + slot);
                if (jobId != null) {
                    plugin.guis().openJobDetail(player, jobId);
                }
            }
            case JOB_DETAIL -> handleJobDetail(player, session, slot);
            case JOB_SELL -> handleJobSell(player, slot);
            default -> {
            }
        }
    }

    private void handleSellClick(InventoryClickEvent event, Player player, GuiSession session) {
        Inventory top = event.getView().getTopInventory();
        int raw = event.getRawSlot();
        if (raw >= 45 && raw < 54) {
            event.setCancelled(true);
            if (raw == 49) {
                sellContents(player, top);
            }
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> plugin.guis().refreshSellEstimate(player, top));
    }

    private void sellContents(Player player, Inventory top) {
        if (!plugin.economy().isEconomyEnabled(player.getUniqueId())) {
            Messages.send(plugin, player, plugin.getConfig().getString("messages.economy-disabled", ""));
            return;
        }
        double total = 0;
        for (int i = 0; i < 45; i++) {
            ItemStack stack = top.getItem(i);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (!plugin.worth().isSellable(stack.getType())) {
                continue;
            }
            total += plugin.worth().valueOf(stack);
            top.setItem(i, null);
        }
        if (total <= 0) {
            Messages.send(plugin, player, "<red>No sellable items (check worth.yml).</red>");
            plugin.guis().refreshSellEstimate(player, top);
            return;
        }
        plugin.economy().deposit(player.getUniqueId(), total);
        Messages.send(plugin, player, "<green>Sold items for " + Messages.money(plugin, total) + ".</green>");
        plugin.logAction(player.getName() + " sold items for " + total);
        player.closeInventory();
    }

    private void handleAuctionClick(Player player, GuiSession session, int slot) {
        if (slot == 45) {
            int page = session.get("page") == null ? 0 : (int) session.get("page");
            plugin.guis().openAuction(player, Math.max(0, page - 1));
            return;
        }
        if (slot == 53) {
            int page = session.get("page") == null ? 0 : (int) session.get("page");
            plugin.guis().openAuction(player, page + 1);
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            plugin.chatInput().beginAuctionPrice(player);
            return;
        }
        Long listingId = session.get("slot:" + slot);
        if (listingId != null) {
            plugin.guis().openAuctionConfirm(player, listingId);
        }
    }

    private void handleConfirm(Player player, GuiSession session, int slot) {
        if (slot == 15) {
            plugin.guis().openAuction(player, 0);
            return;
        }
        if (slot != 11) {
            return;
        }
        long listingId = ((Number) session.get("listingId")).longValue();
        double expected = ((Number) session.get("price")).doubleValue();
        AuctionService.Listing listing = plugin.auctions().getListing(listingId);
        if (listing == null) {
            Messages.send(plugin, player, "<red>That listing no longer exists.</red>");
            plugin.guis().openAuction(player, 0);
            return;
        }
        if (listing.sellerUuid().equals(player.getUniqueId())) {
            // cancel own listing
            if (plugin.auctions().deleteListing(listingId)) {
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(listing.item());
                overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                Messages.send(plugin, player, "<yellow>You cancelled your listing and got the item back.</yellow>");
                plugin.logAction(player.getName() + " cancelled AH#" + listingId);
            }
            plugin.guis().openAuction(player, 0);
            return;
        }
        boolean worker = plugin.jobs().isWorker(player.getUniqueId());
        double price = plugin.auctions().buyerPrice(listing, worker);
        if (Math.abs(price - expected) > 0.011) {
            Messages.send(plugin, player, "<red>Price changed. Please try again.</red>");
            plugin.guis().openAuction(player, 0);
            return;
        }
        if (!plugin.economy().isEconomyEnabled(player.getUniqueId())
                || !plugin.economy().isEconomyEnabled(listing.sellerUuid())) {
            Messages.send(plugin, player, plugin.getConfig().getString("messages.economy-disabled", ""));
            return;
        }
        if (!plugin.economy().has(player.getUniqueId(), price)) {
            Messages.send(plugin, player, plugin.getConfig().getString("messages.insufficient-funds", ""));
            return;
        }
        if (!plugin.auctions().deleteListing(listingId)) {
            Messages.send(plugin, player, "<red>That listing no longer exists.</red>");
            plugin.guis().openAuction(player, 0);
            return;
        }
        plugin.economy().withdraw(player.getUniqueId(), price);
        double payout = plugin.auctions().sellerPayout(price);
        plugin.economy().deposit(listing.sellerUuid(), payout);
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(listing.item());
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        Messages.send(plugin, player, "<green>Purchased for " + Messages.money(plugin, price) + ".</green>");
        Player seller = Bukkit.getPlayer(listing.sellerUuid());
        if (seller != null) {
            Messages.send(plugin, seller, "<green>Your AH listing sold for " + Messages.money(plugin, payout) + ".</green>");
        }
        plugin.logAction(player.getName() + " bought AH#" + listingId + " for " + price
                + " (seller payout " + payout + ")");
        plugin.guis().openAuction(player, 0);
    }

    private void handleJobDetail(Player player, GuiSession session, int slot) {
        if (slot == 15) {
            plugin.guis().openJobs(player);
            return;
        }
        if (slot != 11) {
            return;
        }
        String jobId = session.get("jobId");
        JobDefinition def = plugin.jobs().get(jobId);
        if (def == null) {
            return;
        }
        if (plugin.jobs().getPlayerJob(player.getUniqueId()).hasJob()) {
            Messages.send(plugin, player, "<red>You already have a job.</red>");
            return;
        }
        if (plugin.jobs().isOnCooldown(player.getUniqueId())) {
            Messages.send(plugin, player, "<red>You are still on cooldown after being fired.</red>");
            return;
        }
        if (plugin.jobs().joinJob(player, jobId)) {
            Messages.send(plugin, player, "<green>You joined " + def.displayName() + "!</green>");
            player.closeInventory();
        }
    }

    private void handleJobSell(Player player, int slot) {
        if (slot == 11) {
            if (!plugin.jobs().getPlayerJob(player.getUniqueId()).hasJob()) {
                plugin.guis().openJobs(player);
                return;
            }
            if (plugin.jobs().deliver(player)) {
                Messages.send(plugin, player, "<green>Delivery complete! Deadline refreshed.</green>");
                plugin.guis().openJobSell(player);
            } else {
                Messages.send(plugin, player, "<red>You do not have the required items.</red>");
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        GuiSession session = plugin.guis().session(player);
        if (session == null) {
            return;
        }
        if (session.type() == GuiType.SELL) {
            boolean touchesBottomBar = event.getRawSlots().stream().anyMatch(s -> s >= 45 && s < 54);
            if (touchesBottomBar) {
                event.setCancelled(true);
            } else {
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.guis().refreshSellEstimate(player, event.getView().getTopInventory()));
            }
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        GuiSession session = plugin.guis().session(player);
        if (session == null) {
            return;
        }
        if (session.type() == GuiType.SELL) {
            Inventory top = event.getInventory();
            for (int i = 0; i < 45; i++) {
                ItemStack stack = top.getItem(i);
                if (stack == null || stack.getType().isAir()) {
                    continue;
                }
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
                overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                top.setItem(i, null);
            }
        }
        plugin.guis().clear(player);
    }
}
