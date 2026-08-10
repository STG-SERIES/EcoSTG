package com.ecostg.paper.economy;

import com.ecostg.paper.EcoSTGPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EconomyService {

    public record BalanceEntry(UUID uuid, String name, double balance) {
    }

    private final EcoSTGPlugin plugin;
    private final Database database;

    public EconomyService(EcoSTGPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void ensurePlayer(Player player) {
        ensurePlayer(player.getUniqueId(), player.getName());
    }

    public void ensurePlayer(UUID uuid, String name) {
        try (PreparedStatement ps = database.connection().prepareStatement(
                "INSERT OR IGNORE INTO players(uuid, name, balance) VALUES(?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setDouble(3, plugin.getConfig().getDouble("starting-balance", 500.0));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        updateName(uuid, name);
    }

    public void updateName(UUID uuid, String name) {
        try (PreparedStatement ps = database.connection().prepareStatement(
                "UPDATE players SET name=? WHERE uuid=?")) {
            ps.setString(1, name);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean isEconomyEnabled(UUID uuid) {
        try (PreparedStatement ps = database.connection().prepareStatement(
                "SELECT economy_enabled FROM players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return true;
                }
                return rs.getInt(1) == 1;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void setEconomyEnabled(UUID uuid, boolean enabled) {
        try (PreparedStatement ps = database.connection().prepareStatement(
                "UPDATE players SET economy_enabled=? WHERE uuid=?")) {
            ps.setInt(1, enabled ? 1 : 0);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public double getBalance(UUID uuid) {
        try (PreparedStatement ps = database.connection().prepareStatement(
                "SELECT balance FROM players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean has(UUID uuid, double amount) {
        return getBalance(uuid) + 1e-9 >= amount;
    }

    public void setBalance(UUID uuid, double amount) {
        try (PreparedStatement ps = database.connection().prepareStatement(
                "UPDATE players SET balance=? WHERE uuid=?")) {
            ps.setDouble(1, Math.max(0, amount));
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void deposit(UUID uuid, double amount) {
        if (amount <= 0) {
            return;
        }
        setBalance(uuid, getBalance(uuid) + amount);
    }

    public boolean withdraw(UUID uuid, double amount) {
        if (amount <= 0) {
            return true;
        }
        double bal = getBalance(uuid);
        if (bal + 1e-9 < amount) {
            return false;
        }
        setBalance(uuid, bal - amount);
        return true;
    }

    public boolean transfer(UUID from, UUID to, double amount) {
        if (amount <= 0 || !withdraw(from, amount)) {
            return false;
        }
        deposit(to, amount);
        return true;
    }

    public List<BalanceEntry> topBalances(int limit) {
        List<BalanceEntry> list = new ArrayList<>();
        try (PreparedStatement ps = database.connection().prepareStatement(
                "SELECT uuid, name, balance FROM players ORDER BY balance DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new BalanceEntry(
                            UUID.fromString(rs.getString(1)),
                            rs.getString(2),
                            rs.getDouble(3)
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return list;
    }

    public UUID findUuidByName(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            ensurePlayer(online);
            return online.getUniqueId();
        }
        try (PreparedStatement ps = database.connection().prepareStatement(
                "SELECT uuid FROM players WHERE lower(name)=lower(?)")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return null;
    }

    public String getName(UUID uuid) {
        try (PreparedStatement ps = database.connection().prepareStatement(
                "SELECT name FROM players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return uuid.toString();
    }
}
