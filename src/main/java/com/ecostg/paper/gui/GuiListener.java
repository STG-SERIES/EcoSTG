package com.ecostg.paper.gui;

import com.ecostg.paper.EcoSTGPlugin;
import com.ecostg.paper.economy.AuctionService;
import com.ecostg.paper.job.JobDefinition;
import com.ecostg.paper.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

public final class GuiListener implements Listener {

    private final EcoSTGPlugin plugin;

    public GuiListener(EcoSTGPlugin plugin) {
        this.plugin = plugin;
    }

    private GuiSession resolve(Inventory top) {
        return plugin.guis().sessionFrom(top);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        GuiSession session = resolve(top);
        if (session == null) {
            return;
        }

        GuiType type = session.type();
        if (type == GuiType.SELL) {
            handleSellClick(event, player, top);
            return;
        }

        // Locked menus: never allow taking/moving GUI items
        event.setCancelled(true);
        if (event.getClick() == ClickType.NUMBER_KEY
                || event.getClick() == ClickType.SWAP_OFFHAND
                || event.getClick() == ClickType.DOUBLE_CLICK) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != top) {
            return;
        }

        int slot = event.getRawSlot();
        switch (type) {
            case PAY_PLAYERS -> {
                Object target = session.get("slot:" + slot);
                if (target instanceof java.util.UUID uuid) {
                    player.closeInventory();
                    plugin.chatInput().beginPayAmount(player, uuid);
                }
            }
            case AUCTION -> handleAuctionClick(player, session, slot);
            case AUCTION_CONFIRM -> handleConfirm(player, session, slot);
            case MONEYTOP, ACTIVE_JOBS -> {
            }
            case JOBS -> {
                Object jobId = session.get("slot:" + slot);
                if (jobId instanceof String id) {
                    plugin.guis().openJobDetail(player, id);
                }
            }
            case JOB_DETAIL -> handleJobDetail(player, session, slot);
            case JOB_SELL -> handleJobSell(player, slot);
            default -> {
            }
        }
    }

    private void handleSellClick(InventoryClickEvent event, Player player, Inventory top) {
        int raw = event.getRawSlot();
        int topSize = top.getSize();

        // Only the center deposit slot is interactive for items
        if (raw == 13) {
            return;
        }

        // Block shift-clicks from player inv dumping into filler slots
        if (event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getBottomInventory())
                && event.isShiftClick()) {
            event.setCancelled(true);
            ItemStack current = event.getCurrentItem();
            if (current != null && !current.getType().isAir()) {
                ItemStack inSlot = top.getItem(13);
                if (inSlot == null || inSlot.getType().isAir()) {
                    top.setItem(13, current.clone());
                    event.setCurrentItem(null);
                }
            }
            return;
        }

        if (raw >= 0 && raw < topSize) {
            event.setCancelled(true);
            if (raw == 11) {
                ItemStack item = top.getItem(13);
                if (item == null || item.getType().isAir()) {
                    Messages.send(plugin, player, "<red>Place an item in the middle slot first.</red>");
                    return;
                }
                ItemStack toList = item.clone();
                top.setItem(13, null);
                player.closeInventory();
                plugin.chatInput().beginSellListing(player, toList);
            } else if (raw == 15) {
                player.closeInventory();
            }
        }
    }

    private void handleAuctionClick(Player player, GuiSession session, int slot) {
        if (slot == 45) {
            int page = session.get("page") == null ? 0 : ((Number) session.get("page")).intValue();
            plugin.guis().openAuction(player, Math.max(0, page - 1));
            return;
        }
        if (slot == 53) {
            int page = session.get("page") == null ? 0 : ((Number) session.get("page")).intValue();
            plugin.guis().openAuction(player, page + 1);
            return;
        }
        if (slot == 49) {
            plugin.guis().openSell(player);
            return;
        }
        Object listingId = session.get("slot:" + slot);
        if (listingId instanceof Number id) {
            plugin.guis().openAuctionConfirm(player, id.longValue());
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
            if (!plugin.economy().isEconomyEnabled(player.getUniqueId())) {
                Messages.send(plugin, player, plugin.getConfig().getString("messages.economy-disabled", ""));
                return;
            }
            ItemStack taken = plugin.jobs().takeDeliveryItems(player);
            if (taken == null) {
                Messages.send(plugin, player, "<red>You do not have the required items.</red>");
                return;
            }
            player.closeInventory();
            plugin.chatInput().beginJobSellListing(player, taken);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        GuiSession session = resolve(event.getView().getTopInventory());
        if (session == null) {
            return;
        }
        if (session.type() == GuiType.SELL) {
            boolean onlyDeposit = event.getRawSlots().stream().allMatch(s -> s == 13
                    || s >= event.getView().getTopInventory().getSize());
            if (!onlyDeposit) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof EcoGuiHolder holder)) {
            return;
        }
        GuiSession session = holder.session();
        if (session.type() == GuiType.SELL) {
            ItemStack stack = event.getInventory().getItem(13);
            if (stack != null && !stack.getType().isAir()) {
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
                overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                event.getInventory().setItem(13, null);
            }
        }
        // Defer clear so switching GUIs does not wipe the new session
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof EcoGuiHolder)) {
                plugin.guis().clear(player);
            }
        });
    }
}
