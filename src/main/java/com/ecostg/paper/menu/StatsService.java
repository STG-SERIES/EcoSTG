package com.ecostg.paper.menu;

import com.ecostg.paper.EcoSTGPlugin;
import com.ecostg.paper.economy.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StatsService {

    public record PlayerStats(
            int kills,
            int deaths,
            long playtimeMs,
            int blocksPlaced,
            int blocksBroken,
            int mobsKilled
    ) {
    }

    public record LeaderboardEntry(UUID uuid, String name, long value) {
    }

    public enum LeaderboardType {
        MONEY,
        PLAYTIME,
        KILLS,
        DEATHS,
        BLOCKS_PLACED,
        BLOCKS_BROKEN,
        MOBS_KILLED
    }

    private final EcoSTGPlugin plugin;
    private final Database database;
    private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();

    public StatsService(EcoSTGPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void ensure(UUID uuid) {
        try (PreparedStatement ps = database.connection().prepareStatement(
                "INSERT OR IGNORE INTO player_stats(uuid) VALUES(?)")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void onJoin(UUID uuid) {
        ensure(uuid);
        sessionStart.put(uuid, System.currentTimeMillis());
    }

    public void onQuit(UUID uuid) {
        flushPlaytime(uuid);
        sessionStart.remove(uuid);
    }

    public void flushPlaytime(UUID uuid) {
        Long start = sessionStart.get(uuid);
        if (start == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long delta = Math.max(0, now - start);
        sessionStart.put(uuid, now);
        if (delta <= 0) {
            return;
        }
        ensure(uuid);
        try (PreparedStatement ps = database.connection().prepareStatement(
                "UPDATE player_stats SET playtime_ms = playtime_ms + ? WHERE uuid=?")) {
            ps.setLong(1, delta);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void flushAllOnline() {
        for (UUID uuid : sessionStart.keySet()) {
            flushPlaytime(uuid);
        }
    }

    public void addKill(UUID uuid) {
        bump(uuid, "kills", 1);
    }

    public void addDeath(UUID uuid) {
        bump(uuid, "deaths", 1);
    }

    public void addBlockPlace(UUID uuid) {
        bump(uuid, "blocks_placed", 1);
    }

    public void addBlockBreak(UUID uuid) {
        bump(uuid, "blocks_broken", 1);
    }

    public void addMobKill(UUID uuid) {
        bump(uuid, "mobs_killed", 1);
    }

    private void bump(UUID uuid, String column, int amount) {
        ensure(uuid);
        try (PreparedStatement ps = database.connection().prepareStatement(
                "UPDATE player_stats SET " + column + " = " + column + " + ? WHERE uuid=?")) {
            ps.setInt(1, amount);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public PlayerStats get(UUID uuid) {
        ensure(uuid);
        flushPlaytime(uuid);
        try (PreparedStatement ps = database.connection().prepareStatement(
                "SELECT kills, deaths, playtime_ms, blocks_placed, blocks_broken, mobs_killed "
                        + "FROM player_stats WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new PlayerStats(0, 0, 0, 0, 0, 0);
                }
                return new PlayerStats(
                        rs.getInt(1),
                        rs.getInt(2),
                        rs.getLong(3),
                        rs.getInt(4),
                        rs.getInt(5),
                        rs.getInt(6)
                );
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<LeaderboardEntry> top(LeaderboardType type, int limit) {
        if (type == LeaderboardType.MONEY) {
            List<LeaderboardEntry> out = new ArrayList<>();
            plugin.economy().topBalances(limit).forEach(e ->
                    out.add(new LeaderboardEntry(e.uuid(), e.name(), Math.round(e.balance()))));
            return out;
        }
        flushAllOnline();
        String column = switch (type) {
            case PLAYTIME -> "playtime_ms";
            case KILLS -> "kills";
            case DEATHS -> "deaths";
            case BLOCKS_PLACED -> "blocks_placed";
            case BLOCKS_BROKEN -> "blocks_broken";
            case MOBS_KILLED -> "mobs_killed";
            default -> "kills";
        };
        List<LeaderboardEntry> out = new ArrayList<>();
        try (PreparedStatement ps = database.connection().prepareStatement(
                "SELECT s.uuid, COALESCE(p.name, s.uuid), s." + column
                        + " FROM player_stats s LEFT JOIN players p ON p.uuid = s.uuid "
                        + "ORDER BY s." + column + " DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new LeaderboardEntry(
                            UUID.fromString(rs.getString(1)),
                            rs.getString(2),
                            rs.getLong(3)
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public static String formatPlaytime(long ms) {
        long totalSec = Math.max(0, ms / 1000);
        long hours = totalSec / 3600;
        long minutes = (totalSec % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }
}
