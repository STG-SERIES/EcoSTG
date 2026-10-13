package com.ecostg.paper.menu;

import com.ecostg.paper.EcoSTGPlugin;
import com.ecostg.paper.economy.Database;
import net.kyori.adventure.text.format.TextColor;
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

    public static final String DEFAULT_COLOR = "#55FFFF";

    public record ColorPreset(String name, String hex) {
        public TextColor textColor() {
            return TextColor.fromHexString(hex);
        }
    }

    public static final List<ColorPreset> COLOR_PRESETS = List.of(
            new ColorPreset("Aqua", "#55FFFF"),
            new ColorPreset("White", "#FFFFFF"),
            new ColorPreset("Red", "#FF5555"),
            new ColorPreset("Orange", "#FFAA00"),
            new ColorPreset("Yellow", "#FFFF55"),
            new ColorPreset("Green", "#55FF55"),
            new ColorPreset("Blue", "#5555FF"),
            new ColorPreset("Purple", "#AA00AA"),
            new ColorPreset("Pink", "#FF55FF"),
            new ColorPreset("Gray", "#AAAAAA")
    );

    public record Home(String name, Location location, String color) {
        public TextColor textColor() {
            return parseColor(color);
        }

        public String colorHex() {
            return normalizeColor(color);
        }
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

    /** OPs have no home limit; everyone else uses {@link #maxHomes()}. */
    public int maxHomes(Player player) {
        if (player != null && player.isOp()) {
            return Integer.MAX_VALUE;
        }
        return maxHomes();
    }

    public List<Home> list(UUID uuid) {
        List<Home> homes = new ArrayList<>();
        try (PreparedStatement ps = database.connection().prepareStatement(
                "SELECT name, world, x, y, z, yaw, pitch, icon FROM homes WHERE uuid=? ORDER BY name")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    World world = Bukkit.getWorld(rs.getString(2));
                    if (world == null) {
                        continue;
                    }
                    Location loc = new Location(world, rs.getDouble(3), rs.getDouble(4), rs.getDouble(5),
                            (float) rs.getDouble(6), (float) rs.getDouble(7));
                    homes.add(new Home(rs.getString(1), loc, normalizeColor(rs.getString(8))));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return homes;
    }

    public String nextDefaultName(UUID uuid) {
        List<Home> existing = list(uuid);
        int limit = existing.size() + 100;
        for (int i = 1; i <= limit; i++) {
            String candidate = "Home " + i;
            if (get(uuid, candidate) == null) {
                return candidate;
            }
        }
        return "Home " + (existing.size() + 1);
    }

    public boolean setHome(Player player, String rawName) {
        String name = sanitize(rawName);
        if (name == null) {
            return false;
        }
        List<Home> existing = list(player.getUniqueId());
        Home current = get(player.getUniqueId(), name);
        if (current == null && existing.size() >= maxHomes(player)) {
            return false;
        }
        Location loc = player.getLocation();
        if (current != null) {
            try (PreparedStatement ps = database.connection().prepareStatement("""
                    UPDATE homes SET world=?, x=?, y=?, z=?, yaw=?, pitch=?
                    WHERE uuid=? AND lower(name)=lower(?)
                    """)) {
                ps.setString(1, loc.getWorld().getName());
                ps.setDouble(2, loc.getX());
                ps.setDouble(3, loc.getY());
                ps.setDouble(4, loc.getZ());
                ps.setDouble(5, loc.getYaw());
                ps.setDouble(6, loc.getPitch());
                ps.setString(7, player.getUniqueId().toString());
                ps.setString(8, current.name());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
            plugin.logAction(player.getName() + " updated home '" + current.name() + "'");
            return true;
        }
        try (PreparedStatement ps = database.connection().prepareStatement("""
                INSERT INTO homes(uuid, name, world, x, y, z, yaw, pitch, icon)
                VALUES(?,?,?,?,?,?,?,?,?)
                """)) {
            ps.setString(1, player.getUniqueId().toString());
            ps.setString(2, name);
            ps.setString(3, loc.getWorld().getName());
            ps.setDouble(4, loc.getX());
            ps.setDouble(5, loc.getY());
            ps.setDouble(6, loc.getZ());
            ps.setDouble(7, loc.getYaw());
            ps.setDouble(8, loc.getPitch());
            ps.setString(9, DEFAULT_COLOR);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        plugin.logAction(player.getName() + " set home '" + name + "'");
        return true;
    }

    public boolean rename(UUID uuid, String oldRaw, String newRaw) {
        Home home = get(uuid, oldRaw);
        String newName = sanitize(newRaw);
        if (home == null || newName == null) {
            return false;
        }
        if (home.name().equalsIgnoreCase(newName)) {
            if (home.name().equals(newName)) {
                return true;
            }
        } else {
            Home clash = get(uuid, newName);
            if (clash != null) {
                return false;
            }
        }
        try (PreparedStatement ps = database.connection().prepareStatement(
                "UPDATE homes SET name=? WHERE uuid=? AND lower(name)=lower(?)")) {
            ps.setString(1, newName);
            ps.setString(2, uuid.toString());
            ps.setString(3, home.name());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean setColor(UUID uuid, String rawName, String rawColor) {
        Home home = get(uuid, rawName);
        String color = normalizeColor(rawColor);
        if (home == null) {
            return false;
        }
        try (PreparedStatement ps = database.connection().prepareStatement(
                "UPDATE homes SET icon=? WHERE uuid=? AND lower(name)=lower(?)")) {
            ps.setString(1, color);
            ps.setString(2, uuid.toString());
            ps.setString(3, home.name());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean deleteHome(UUID uuid, String rawName) {
        Home home = get(uuid, rawName);
        if (home == null) {
            return false;
        }
        try (PreparedStatement ps = database.connection().prepareStatement(
                "DELETE FROM homes WHERE uuid=? AND lower(name)=lower(?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, home.name());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Home get(UUID uuid, String rawName) {
        if (rawName == null) {
            return null;
        }
        String wanted = rawName.trim();
        if (wanted.isEmpty()) {
            return null;
        }
        for (Home home : list(uuid)) {
            if (home.name().equalsIgnoreCase(wanted)) {
                return home;
            }
        }
        return null;
    }

    public boolean teleport(Player player, String rawName) {
        if (plugin.combat().denyIfInCombat(player)) {
            return false;
        }
        Home home = get(player.getUniqueId(), rawName);
        if (home == null) {
            return false;
        }
        player.teleport(home.location());
        plugin.logAction(player.getName() + " teleported to home '" + home.name() + "'");
        return true;
    }

    public static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > 32) {
            return null;
        }
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9 _\\-]*")) {
            return null;
        }
        return name;
    }

    public static String normalizeColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_COLOR;
        }
        String value = raw.trim();
        for (ColorPreset preset : COLOR_PRESETS) {
            if (preset.name().equalsIgnoreCase(value) || preset.hex().equalsIgnoreCase(value)) {
                return preset.hex();
            }
        }
        if (!value.startsWith("#")) {
            value = "#" + value;
        }
        value = value.toUpperCase(Locale.ROOT);
        if (!value.matches("#[0-9A-F]{6}")) {
            return DEFAULT_COLOR;
        }
        return value;
    }

    public static TextColor parseColor(String raw) {
        TextColor color = TextColor.fromHexString(normalizeColor(raw));
        return color != null ? color : TextColor.fromHexString(DEFAULT_COLOR);
    }

    public static String parseHexInput(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (!value.startsWith("#")) {
            value = "#" + value;
        }
        value = value.toUpperCase(Locale.ROOT);
        if (!value.matches("#[0-9A-F]{6}")) {
            return null;
        }
        return value;
    }

    public static String presetLabel(String hex) {
        String normalized = normalizeColor(hex);
        for (ColorPreset preset : COLOR_PRESETS) {
            if (preset.hex().equalsIgnoreCase(normalized)) {
                return preset.name();
            }
        }
        return normalized;
    }
}
