package com.ecostg.paper.economy;

import com.ecostg.paper.EcoSTGPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AuctionService {

    public record Listing(long id, UUID sellerUuid, String sellerName, double price,
                          boolean workerListing, boolean instantPaid, ItemStack item, long createdAt) {
    }

    private final EcoSTGPlugin plugin;
    private final Database database;
    private final EconomyService economy;

    public AuctionService(EcoSTGPlugin plugin, Database database, EconomyService economy) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
    }

    public long createListing(Player seller, ItemStack item, double price, boolean workerListing) {
        return createListing(seller, item, price, workerListing, false);
    }

    public long createListing(Player seller, ItemStack item, double price, boolean workerListing,
                              boolean instantPaid) {
        ItemStack clean = plugin.worth().undecoratedClone(item);
        if (clean == null || clean.getType().isAir()) {
            return -1;
        }
        byte[] bytes = clean.serializeAsBytes();
        try (PreparedStatement ps = database.connection().prepareStatement(
                "INSERT INTO auctions(seller_uuid, seller_name, price, worker_listing, instant_paid, item_bytes, created_at) VALUES(?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, seller.getUniqueId().toString());
            ps.setString(2, seller.getName());
            ps.setDouble(3, price);
            ps.setInt(4, workerListing ? 1 : 0);
            ps.setInt(5, instantPaid ? 1 : 0);
            ps.setBytes(6, bytes);
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return -1;
    }

    public Listing getListing(long id) {
        try (PreparedStatement ps = database.connection().prepareStatement(
                "SELECT id, seller_uuid, seller_name, price, worker_listing, instant_paid, item_bytes, created_at FROM auctions WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return map(rs);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Listing> listAll(int offset, int limit) {
        return listFiltered("", offset, limit);
    }

    public List<Listing> listFiltered(String rawQuery, int offset, int limit) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        List<Listing> matched = new ArrayList<>();
        try (PreparedStatement ps = database.connection().prepareStatement(
                "SELECT id, seller_uuid, seller_name, price, worker_listing, instant_paid, item_bytes, created_at FROM auctions ORDER BY id DESC")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Listing listing = map(rs);
                    if (query.isEmpty() || matchesSearch(listing, query)) {
                        matched.add(listing);
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        if (offset >= matched.size()) {
            return List.of();
        }
        int to = Math.min(matched.size(), offset + Math.max(0, limit));
        return new ArrayList<>(matched.subList(Math.max(0, offset), to));
    }

    private boolean matchesSearch(Listing listing, String query) {
        if (listing.sellerName() != null
                && listing.sellerName().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        return matchesItemSearch(listing.item(), query);
    }

    private boolean matchesItemSearch(ItemStack item, String query) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        String material = item.getType().name().toLowerCase(Locale.ROOT);
        String pretty = material.replace('_', ' ');
        if (material.contains(query) || pretty.contains(query)) {
            return true;
        }
        var meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String display = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(meta.displayName()).toLowerCase(Locale.ROOT);
            if (display.contains(query)) {
                return true;
            }
        }
        for (ItemStack inner : plugin.worth().shulkerContents(item)) {
            if (matchesItemSearch(inner, query)) {
                return true;
            }
        }
        return false;
    }

    public int count() {
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT COUNT(*) FROM auctions");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean deleteListing(long id) {
        try (PreparedStatement ps = database.connection().prepareStatement("DELETE FROM auctions WHERE id=?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public double buyerPrice(Listing listing, boolean buyerIsWorker) {
        double price = listing.price();
        if (listing.workerListing()) {
            double listingDiscount = plugin.getConfig().getDouble("jobs.worker-listing-discount-percent", 5.0);
            price = price * (1.0 - listingDiscount / 100.0);
        }
        if (buyerIsWorker) {
            double buyerDiscount = plugin.getConfig().getDouble("jobs.ah-buyer-discount-percent", 8.0);
            price = price * (1.0 - buyerDiscount / 100.0);
        }
        return Math.max(0.01, Math.round(price * 100.0) / 100.0);
    }

    public double sellerPayout(double buyerPaid) {
        double tax = plugin.getConfig().getDouble("auction.sale-tax-percent", 5.0);
        return Math.max(0, Math.round(buyerPaid * (1.0 - tax / 100.0) * 100.0) / 100.0);
    }

    private Listing map(ResultSet rs) throws SQLException {
        ItemStack item = ItemStack.deserializeBytes(rs.getBytes("item_bytes"));
        plugin.worth().stripSellLore(item);
        return new Listing(
                rs.getLong("id"),
                UUID.fromString(rs.getString("seller_uuid")),
                rs.getString("seller_name"),
                rs.getDouble("price"),
                rs.getInt("worker_listing") == 1,
                rs.getInt("instant_paid") == 1,
                item,
                rs.getLong("created_at")
        );
    }

    public EconomyService economy() {
        return economy;
    }
}
