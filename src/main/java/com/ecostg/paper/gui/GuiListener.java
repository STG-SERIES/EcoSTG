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
        if (type == GuiType.AUCTION_LIST) {
            handleAuctionListClick(event, player, top);
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

        // Sell confirm button
        if (raw == GuiManager.SELL_BUTTON_SLOT) {
            event.setCancelled(true);
            GuiManager.SellResult result = plugin.guis().sellContents(player, top);
            if (result.listed() <= 0) {
                Messages.send(plugin, player, "<red>No sellable items to list on the auction house.</red>");
                plugin.guis().refreshSellGui(top);
                return;
            }
            Messages.send(plugin, player, "<green>Sold <white>" + result.listed()
                    + "</white> stack(s) for <white>" + Messages.money(plugin, result.paid())
                    + "</white> (listed on AH).</green>");
            plugin.guis().refreshSellGui(top);
            return;
        }

        // Allow free placement in item slots 0..52; block dumping onto the button via shift-click
        if (raw >= 0 && raw < topSize && raw != GuiManager.SELL_BUTTON_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.worth().stripPlayerInventory(player);
                plugin.guis().refreshSellGui(top);
            });
            return;
        }

        if (event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getBottomInventory())
                && event.isShiftClick()) {
            // Shift-click into top: cancel default, fill empty sell slots manually so button stays free
            event.setCancelled(true);
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType().isAir()) {
                return;
            }
            ItemStack moving = current.clone();
            plugin.worth().stripSellLore(moving);
            for (int i = 0; i < GuiManager.SELL_ITEM_SLOTS && moving.getAmount() > 0; i++) {
                ItemStack inSlot = top.getItem(i);
                if (inSlot == null || inSlot.getType().isAir()) {
                    top.setItem(i, moving);
                    moving = null;
                    event.setCurrentItem(null);
                    break;
                }
                ItemStack compare = inSlot.clone();
                plugin.worth().stripSellLore(compare);
                if (compare.isSimilar(moving)) {
                    int max = inSlot.getMaxStackSize();
                    int space = max - inSlot.getAmount();
                    if (space <= 0) {
                        continue;
                    }
                    int move = Math.min(space, moving.getAmount());
                    inSlot.setAmount(inSlot.getAmount() + move);
                    moving.setAmount(moving.getAmount() - move);
                    top.setItem(i, inSlot);
                    if (moving.getAmount() <= 0) {
                        event.setCurrentItem(null);
                        moving = null;
                        break;
                    }
                }
            }
            if (moving != null && moving.getAmount() > 0) {
                event.setCurrentItem(moving);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.worth().stripPlayerInventory(player);
                plugin.guis().refreshSellGui(top);
            });
            return;
        }

        if (raw >= 0 && raw < topSize) {
            event.setCancelled(true);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.worth().stripPlayerInventory(player);
                plugin.guis().refreshSellGui(top);
            });
        }
    }

    private void handleAuctionListClick(InventoryClickEvent event, Player player, Inventory top) {
        int raw = event.getRawSlot();
        int topSize = top.getSize();

        if (raw == 13) {
            return;
        }

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
        String search = session.get("search");
        if (search == null) {
            search = "";
        }
        int page = session.get("page") == null ? 0 : ((Number) session.get("page")).intValue();
        if (slot == 45) {
            plugin.guis().openAuction(player, Math.max(0, page - 1), search);
            return;
        }
        if (slot == 53) {
            plugin.guis().openAuction(player, page + 1, search);
            return;
        }
        if (slot == 49) {
            plugin.guis().openSell(player);
            return;
        }
        Object listingId = session.get("slot:" + slot);
        if (listingId instanceof Number id) {
            tryBuyOrCancelListing(player, id.longValue(), page, search);
        }
    }

    private void tryBuyOrCancelListing(Player player, long listingId, int page, String search) {
        AuctionService.Listing listing = plugin.auctions().getListing(listingId);
        if (listing == null) {
            Messages.send(plugin, player, "<red>That listing no longer exists.</red>");
            reopenAuction(player, page, search);
            return;
        }
        if (listing.sellerUuid().equals(player.getUniqueId())) {
            if (listing.instantPaid()) {
                if (!plugin.economy().has(player.getUniqueId(), listing.price())) {
                    Messages.send(plugin, player, "<red>You need "
                            + Messages.money(plugin, listing.price())
                            + " to cancel this prepaid listing (you were paid when you sold).</red>");
                    return;
                }
                if (!plugin.auctions().deleteListing(listingId)) {
                    Messages.send(plugin, player, "<red>That listing no longer exists.</red>");
                    reopenAuction(player, page, search);
                    return;
                }
                plugin.economy().withdraw(player.getUniqueId(), listing.price());
                giveCleanItem(player, listing.item());
                Messages.send(plugin, player, "<yellow>Cancelled prepaid listing. "
                        + Messages.money(plugin, listing.price()) + " returned from your balance.</yellow>");
                plugin.logAction(player.getName() + " cancelled prepaid AH#" + listingId);
            } else if (plugin.auctions().deleteListing(listingId)) {
                giveCleanItem(player, listing.item());
                Messages.send(plugin, player, "<yellow>You cancelled your listing and got the item back.</yellow>");
                plugin.logAction(player.getName() + " cancelled AH#" + listingId);
            }
            reopenAuction(player, page, search);
            return;
        }
        boolean worker = plugin.jobs().isWorker(player.getUniqueId());
        double price = plugin.auctions().buyerPrice(listing, worker);
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
            reopenAuction(player, page, search);
            return;
        }
        plugin.economy().withdraw(player.getUniqueId(), price);
        if (!listing.instantPaid()) {
            double payout = plugin.auctions().sellerPayout(price);
            plugin.economy().deposit(listing.sellerUuid(), payout);
            Player seller = Bukkit.getPlayer(listing.sellerUuid());
            if (seller != null) {
                Messages.send(plugin, seller, "<green>Your AH listing sold for "
                        + Messages.money(plugin, payout) + ".</green>");
            }
            plugin.logAction(player.getName() + " bought AH#" + listingId + " for " + price
                    + " (seller payout " + payout + ")");
        } else {
            Player seller = Bukkit.getPlayer(listing.sellerUuid());
            if (seller != null) {
                Messages.send(plugin, seller, "<green>Your prepaid AH listing was purchased.</green>");
            }
            plugin.logAction(player.getName() + " bought prepaid AH#" + listingId + " for " + price);
        }
        giveCleanItem(player, listing.item());
        Messages.send(plugin, player, "<green>Purchased for " + Messages.money(plugin, price) + ".</green>");
        reopenAuction(player, page, search);
    }

    private void reopenAuction(Player player, int page, String search) {
        String query = search == null ? "" : search;
        int safePage = Math.max(0, page);
        if (safePage > 0 && plugin.auctions().listFiltered(query, safePage * 45, 45).isEmpty()) {
            safePage--;
        }
        plugin.guis().openAuction(player, Math.max(0, safePage), query);
    }

    private void giveCleanItem(Player player, ItemStack raw) {
        ItemStack clean = plugin.worth().undecoratedClone(raw);
        if (clean == null || clean.getType().isAir()) {
            return;
        }
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(clean);
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
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
            if (event.getRawSlots().contains(GuiManager.SELL_BUTTON_SLOT)) {
                event.setCancelled(true);
                return;
            }
            Inventory top = event.getView().getTopInventory();
            Player player = (Player) event.getWhoClicked();
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.worth().stripPlayerInventory(player);
                plugin.guis().refreshSellGui(top);
            });
            return;
        }
        if (session.type() == GuiType.AUCTION_LIST) {
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
            plugin.guis().returnSellItems(player, event.getInventory());
            plugin.worth().stripPlayerInventory(player);
        } else if (session.type() == GuiType.AUCTION_LIST) {
            ItemStack stack = event.getInventory().getItem(13);
            if (stack != null && !stack.getType().isAir()) {
                giveCleanItem(player, stack);
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
