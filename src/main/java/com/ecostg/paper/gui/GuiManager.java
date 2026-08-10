package com.ecostg.paper.gui;

import com.ecostg.paper.EcoSTGPlugin;
import com.ecostg.paper.economy.AuctionService;
import com.ecostg.paper.economy.EconomyService;
import com.ecostg.paper.job.JobDefinition;
import com.ecostg.paper.job.JobService;
import com.ecostg.paper.util.ItemUtil;
import com.ecostg.paper.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class GuiManager {

    private final EcoSTGPlugin plugin;
    private final Map<UUID, GuiSession> sessions = new HashMap<>();

    public GuiManager(EcoSTGPlugin plugin) {
        this.plugin = plugin;
    }

    public GuiSession session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public void clear(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public boolean canUseEconomy(Player player) {
        plugin.economy().ensurePlayer(player);
        return plugin.economy().isEconomyEnabled(player.getUniqueId());
    }

    public void openPay(Player player) {
        if (!canUseEconomy(player)) {
            Messages.send(plugin, player, plugin.getConfig().getString("messages.economy-disabled", ""));
            return;
        }
        GuiSession session = new GuiSession(GuiType.PAY_PLAYERS);
        Inventory inv = Bukkit.createInventory(player, 54, Messages.mm("<dark_green>Pay a Player"));
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId()) || slot >= 45) {
                continue;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(online);
            meta.displayName(Messages.mm("<green>" + online.getName()));
            meta.lore(List.of(
                    Messages.mm("<gray>Balance: <white>" + Messages.money(plugin, plugin.economy().getBalance(online.getUniqueId()))),
                    Messages.mm("<yellow>Click to pay")
            ));
            head.setItemMeta(meta);
            inv.setItem(slot, head);
            session.put("slot:" + slot, online.getUniqueId());
            slot++;
        }
        inv.setItem(49, ItemUtil.named(Material.GOLD_INGOT, "<gold>Your balance",
                List.of("<white>" + Messages.money(plugin, plugin.economy().getBalance(player.getUniqueId())))));
        sessions.put(player.getUniqueId(), session);
        player.openInventory(inv);
    }

    public void openMoneyTop(Player player) {
        if (!canUseEconomy(player)) {
            Messages.send(plugin, player, plugin.getConfig().getString("messages.economy-disabled", ""));
            return;
        }
        GuiSession session = new GuiSession(GuiType.MONEYTOP);
        Inventory inv = Bukkit.createInventory(player, 27, Messages.mm("<gold>Top 10 Richest"));
        List<EconomyService.BalanceEntry> top = plugin.economy().topBalances(10);
        int[] slots = {2, 3, 4, 5, 6, 11, 12, 13, 14, 15};
        for (int i = 0; i < top.size() && i < slots.length; i++) {
            EconomyService.BalanceEntry entry = top.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.uuid()));
            meta.displayName(Messages.mm("<yellow>#" + (i + 1) + " <white>" + entry.name()));
            meta.lore(List.of(Messages.mm("<green>" + Messages.money(plugin, entry.balance()))));
            head.setItemMeta(meta);
            inv.setItem(slots[i], head);
        }
        sessions.put(player.getUniqueId(), session);
        player.openInventory(inv);
    }

    public void openAuction(Player player, int page) {
        if (!canUseEconomy(player)) {
            Messages.send(plugin, player, plugin.getConfig().getString("messages.economy-disabled", ""));
            return;
        }
        GuiSession session = new GuiSession(GuiType.AUCTION);
        session.put("page", page);
        Inventory inv = Bukkit.createInventory(player, 54, Messages.mm("<dark_aqua>Auction House"));
        boolean worker = plugin.jobs().isWorker(player.getUniqueId());
        List<AuctionService.Listing> listings = plugin.auctions().listAll(page * 45, 45);
        for (int i = 0; i < listings.size(); i++) {
            AuctionService.Listing listing = listings.get(i);
            ItemStack display = listing.item().clone();
            var meta = display.getItemMeta();
            List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Messages.mm("<dark_gray>-------------"));
            lore.add(Messages.mm("<gray>Seller: <white>" + listing.sellerName()));
            double price = plugin.auctions().buyerPrice(listing, worker);
            lore.add(Messages.mm("<gray>Price: <green>" + Messages.money(plugin, price)));
            if (listing.workerListing()) {
                lore.add(Messages.mm("<aqua>By a worker"));
            }
            lore.add(Messages.mm("<yellow>Click to buy"));
            meta.lore(lore);
            display.setItemMeta(meta);
            inv.setItem(i, display);
            session.put("slot:" + i, listing.id());
        }
        inv.setItem(45, ItemUtil.named(Material.ARROW, "<yellow>Previous Page", List.of()));
        inv.setItem(49, ItemUtil.named(Material.EMERALD, "<green>List an Item",
                List.of("<gray>Put an item in your hand", "<gray>then click here and type a price")));
        inv.setItem(53, ItemUtil.named(Material.ARROW, "<yellow>Next Page", List.of()));
        sessions.put(player.getUniqueId(), session);
        player.openInventory(inv);
    }

    public void openAuctionConfirm(Player player, long listingId) {
        AuctionService.Listing listing = plugin.auctions().getListing(listingId);
        if (listing == null) {
            Messages.send(plugin, player, "<red>That listing no longer exists.</red>");
            openAuction(player, 0);
            return;
        }
        boolean worker = plugin.jobs().isWorker(player.getUniqueId());
        double price = plugin.auctions().buyerPrice(listing, worker);
        GuiSession session = new GuiSession(GuiType.AUCTION_CONFIRM);
        session.put("listingId", listingId);
        session.put("price", price);
        Inventory inv = Bukkit.createInventory(player, 27, Messages.mm("<red>Confirm Purchase"));
        ItemStack item = listing.item().clone();
        inv.setItem(13, item);
        inv.setItem(11, ItemUtil.named(Material.LIME_CONCRETE, "<green>Confirm",
                List.of("<white>Buy " + item.getAmount() + "x " + pretty(item.getType()),
                        "<white>for " + Messages.money(plugin, price) + "?",
                        "<gray>Are you sure you want to buy this?")));
        inv.setItem(15, ItemUtil.named(Material.RED_CONCRETE, "<red>Cancel", List.of()));
        sessions.put(player.getUniqueId(), session);
        player.openInventory(inv);
    }

    public void openSell(Player player) {
        if (!canUseEconomy(player)) {
            Messages.send(plugin, player, plugin.getConfig().getString("messages.economy-disabled", ""));
            return;
        }
        GuiSession session = new GuiSession(GuiType.SELL);
        Inventory inv = Bukkit.createInventory(player, 54, Messages.mm("<gold>Sell Items"));
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, ItemUtil.filler());
        }
        inv.setItem(49, ItemUtil.named(Material.GOLD_INGOT, "<yellow>Estimated: <white>$0.00",
                List.of("<gray>Place items above", "<green>Click here to sell")));
        sessions.put(player.getUniqueId(), session);
        player.openInventory(inv);
    }

    public void refreshSellEstimate(Player player, Inventory inv) {
        ItemStack[] contents = new ItemStack[45];
        for (int i = 0; i < 45; i++) {
            contents[i] = inv.getItem(i);
        }
        double total = plugin.worth().valueOf(contents);
        inv.setItem(49, ItemUtil.named(Material.GOLD_INGOT,
                "<yellow>Estimated: <white>" + Messages.money(plugin, total),
                List.of("<gray>Place items above", "<green>Click here to sell")));
    }

    public void openJobs(Player player) {
        GuiSession session = new GuiSession(GuiType.JOBS);
        Inventory inv = Bukkit.createInventory(player, 27, Messages.mm("<dark_green>Jobs"));
        int slot = 10;
        for (JobDefinition job : plugin.jobs().jobs().values()) {
            if (slot > 16) {
                break;
            }
            List<String> lore = new ArrayList<>(job.description());
            lore.add("<gray>Deliver: <white>" + job.requiredAmount() + "x " + pretty(job.requiredMaterial()));
            lore.add("<yellow>Click for details");
            inv.setItem(slot, ItemUtil.named(job.icon(), "<green>" + job.displayName(), lore));
            session.put("slot:" + slot, job.id());
            slot++;
        }
        sessions.put(player.getUniqueId(), session);
        player.openInventory(inv);
    }

    public void openJobDetail(Player player, String jobId) {
        JobDefinition job = plugin.jobs().get(jobId);
        if (job == null) {
            openJobs(player);
            return;
        }
        GuiSession session = new GuiSession(GuiType.JOB_DETAIL);
        session.put("jobId", jobId);
        Inventory inv = Bukkit.createInventory(player, 27, Messages.mm("<green>" + job.displayName()));
        List<String> lore = new ArrayList<>(job.description());
        lore.add("<gray>Required every " + plugin.getConfig().getInt("jobs.deadline-ingame-days", 4) + " in-game days:");
        lore.add("<white>" + job.requiredAmount() + "x " + pretty(job.requiredMaterial()));
        inv.setItem(13, ItemUtil.named(job.icon(), "<green>" + job.displayName(), lore));
        inv.setItem(11, ItemUtil.named(Material.LIME_CONCRETE, "<green>Get Job", List.of("<gray>Join this job")));
        inv.setItem(15, ItemUtil.named(Material.ARROW, "<yellow>Back", List.of()));
        sessions.put(player.getUniqueId(), session);
        player.openInventory(inv);
    }

    public void openJobSell(Player player) {
        GuiSession session = new GuiSession(GuiType.JOB_SELL);
        Inventory inv = Bukkit.createInventory(player, 27, Messages.mm("<dark_green>Job Delivery"));
        JobService.PlayerJob job = plugin.jobs().getPlayerJob(player.getUniqueId());
        if (!job.hasJob()) {
            inv.setItem(13, ItemUtil.named(Material.BARRIER, "<red>No active job",
                    List.of("<gray>Use /job to find work")));
            inv.setItem(11, ItemUtil.named(Material.EMERALD, "<green>Get Job", List.of("<gray>Open jobs menu")));
        } else {
            JobDefinition def = plugin.jobs().get(job.jobId());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>You have to sell <white>" + def.requiredAmount() + "x " + pretty(def.requiredMaterial()));
            lore.add("<gray>Deadline world day: <white>" + job.deadlineDay());
            lore.add("<gray>Current world day: <white>" + plugin.jobs().currentWorldDay());
            inv.setItem(13, ItemUtil.named(def.icon(), "<green>" + def.displayName(), lore));
            inv.setItem(11, ItemUtil.named(Material.LIME_CONCRETE, "<green>Deliver Items",
                    List.of("<gray>Takes required items from your inventory")));
        }
        if (plugin.jobs().isOnCooldown(player.getUniqueId())) {
            long days = TimeUnit.MILLISECONDS.toDays(plugin.jobs().cooldownRemainingMs(player.getUniqueId())) + 1;
            inv.setItem(15, ItemUtil.named(Material.CLOCK, "<red>Fired cooldown",
                    List.of("<gray>Can rejoin in about <white>" + days + " day(s)")));
        }
        sessions.put(player.getUniqueId(), session);
        player.openInventory(inv);
    }

    public static String pretty(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String part : name.split(" ")) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
