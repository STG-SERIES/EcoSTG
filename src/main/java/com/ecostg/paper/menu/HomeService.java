package com.ecostg.paper.menu;

import com.ecostg.paper.EcoSTGPlugin;
import com.ecostg.paper.economy.Database;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class HomeService {

    public record Home(String name, Location location) {
    }

    private final EcoSTGPlugin plugin;
    private final Database database;

    public HomeService(EcoSTGPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public int maxHomes() {
        return Math.max(1, plugin.getConfig().getInt("homes.max", 3));
    }

    public List<Home> list(UUID uuid) {
        List<Home> homes = new ArrayList<>();
        try (PreparedStatement ps = database.connection().prepareStatement(
                "SELECT name, world, x, y, z, yaw, pitch FROM homes WHERE uuid=? ORDER BY name")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    World world = Bukkit.getWorld(rs.getString(2));
                    if (world == null) {
                        continue;
                    }
                    Location loc = new Location(world, rs.getDouble(3), rs.getDouble(4), rs.getDouble(5),
                            (float) rs.getDouble(6), (float) rs.getDouble(7));
                    homes.add(new Home(rs.getString(1), loc));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return homes;
    }

    public boolean setHome(Player player, String rawName) {
        String name = sanitize(rawName);
        if (name == null) {
            return false;
        }
        List<Home> existing = list(player.getUniqueId());
        boolean overwrite = existing.stream().anyMatch(h -> h.name().equalsIgnoreCase(name));
        if (!overwrite && existing.size() >= maxHomes()) {
            return false;
        }
        Location loc = player.getLocation();
        try (PreparedStatement ps = database.connection().prepareStatement("""
                INSERT INTO homes(uuid, name, world, x, y, z, yaw, pitch)
                VALUES(?,?,?,?,?,?,?,?)
                ON CONFLICT(uuid, name) DO UPDATE SET
                  world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z,
                  yaw=excluded.yaw, pitch=excluded.pitch
                """)) {
            ps.setString(1, player.getUniqueId().toString());
            ps.setString(2, name);
            ps.setString(3, loc.getWorld().getName());
            ps.setDouble(4, loc.getX());
            ps.setDouble(5, loc.getY());
            ps.setDouble(6, loc.getZ());
            ps.setDouble(7, loc.getYaw());
            ps.setDouble(8, loc.getPitch());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        plugin.logAction(player.getName() + " set home '" + name + "'");
        return true;
    }

    public boolean deleteHome(UUID uuid, String rawName) {
        String name = sanitize(rawName);
        if (name == null) {
            return false;
        }
        try (PreparedStatement ps = database.connection().prepareStatement(
                "DELETE FROM homes WHERE uuid=? AND lower(name)=lower(?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Home get(UUID uuid, String rawName) {
        String name = sanitize(rawName);
        if (name == null) {
            return null;
        }
        for (Home home : list(uuid)) {
            if (home.name().equalsIgnoreCase(name)) {
                return home;
            }
        }
        return null;
    }

    public boolean teleport(Player player, String rawName) {
        Home home = get(player.getUniqueId(), rawName);
        if (home == null) {
            return false;
        }
        player.teleport(home.location());
        plugin.logAction(player.getName() + " teleported to home '" + home.name() + "'");
        return true;
    }

    private static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.trim().replace(' ', '_');
        if (name.isEmpty() || name.length() > 16) {
            return null;
        }
        if (!name.matches("[A-Za-z0-9_]+")) {
            return null;
        }
        return name.toLowerCase(Locale.ROOT);
    }
}
