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
import java.util.UUID;

public final class AuctionService {

    public record Listing(long id, UUID sellerUuid, String sellerName, double price,
                          boolean workerListing, ItemStack item, long createdAt) {
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
        byte[] bytes = item.serializeAsBytes();
        try (PreparedStatement ps = database.connection().prepareStatement(
                "INSERT INTO auctions(seller_uuid, seller_name, price, worker_listing, item_bytes, created_at) VALUES(?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, seller.getUniqueId().toString());
            ps.setString(2, seller.getName());
            ps.setDouble(3, price);
            ps.setInt(4, workerListing ? 1 : 0);
            ps.setBytes(5, bytes);
            ps.setLong(6, System.currentTimeMillis());
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
                "SELECT id, seller_uuid, seller_name, price, worker_listing, item_bytes, created_at FROM auctions WHERE id=?")) {
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
        List<Listing> list = new ArrayList<>();
        try (PreparedStatement ps = database.connection().prepareStatement(
                "SELECT id, seller_uuid, seller_name, price, worker_listing, item_bytes, created_at FROM auctions ORDER BY id DESC LIMIT ? OFFSET ?")) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return list;
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
        return new Listing(
                rs.getLong("id"),
                UUID.fromString(rs.getString("seller_uuid")),
                rs.getString("seller_name"),
                rs.getDouble("price"),
                rs.getInt("worker_listing") == 1,
                item,
                rs.getLong("created_at")
        );
    }

    public EconomyService economy() {
        return economy;
    }
}
