package com.ecostg.paper.economy;

import com.ecostg.paper.EcoSTGPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {

    private final EcoSTGPlugin plugin;
    private Connection connection;

    public Database(EcoSTGPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            File folder = plugin.getDataFolder();
            if (!folder.exists() && !folder.mkdirs()) {
                throw new IllegalStateException("Could not create plugin data folder");
            }
            File dbFile = new File(folder, "ecostg.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS players (
                          uuid TEXT PRIMARY KEY,
                          name TEXT NOT NULL,
                          balance REAL NOT NULL,
                          economy_enabled INTEGER NOT NULL DEFAULT 1,
                          job_id TEXT,
                          job_start_day INTEGER,
                          job_deadline_day INTEGER,
                          job_cooldown_until INTEGER NOT NULL DEFAULT 0
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS auctions (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          seller_uuid TEXT NOT NULL,
                          seller_name TEXT NOT NULL,
                          price REAL NOT NULL,
                          worker_listing INTEGER NOT NULL DEFAULT 0,
                          instant_paid INTEGER NOT NULL DEFAULT 0,
                          item_bytes BLOB NOT NULL,
                          created_at INTEGER NOT NULL
                        )
                        """);
                addColumnIfMissing(st, "auctions", "instant_paid",
                        "ALTER TABLE auctions ADD COLUMN instant_paid INTEGER NOT NULL DEFAULT 0");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS homes (
                          uuid TEXT NOT NULL,
                          name TEXT NOT NULL,
                          world TEXT NOT NULL,
                          x REAL NOT NULL,
                          y REAL NOT NULL,
                          z REAL NOT NULL,
                          yaw REAL NOT NULL,
                          pitch REAL NOT NULL,
                          icon TEXT NOT NULL DEFAULT '#55FFFF',
                          PRIMARY KEY(uuid, name)
                        )
                        """);
                // icon column stores home color hex (#RRGGBB); kept name for existing DBs
                addColumnIfMissing(st, "homes", "icon",
                        "ALTER TABLE homes ADD COLUMN icon TEXT NOT NULL DEFAULT '#55FFFF'");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS friends (
                          uuid TEXT NOT NULL,
                          friend_uuid TEXT NOT NULL,
                          PRIMARY KEY(uuid, friend_uuid)
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_stats (
                          uuid TEXT PRIMARY KEY,
                          kills INTEGER NOT NULL DEFAULT 0,
                          deaths INTEGER NOT NULL DEFAULT 0,
                          playtime_ms INTEGER NOT NULL DEFAULT 0,
                          blocks_placed INTEGER NOT NULL DEFAULT 0,
                          blocks_broken INTEGER NOT NULL DEFAULT 0,
                          mobs_killed INTEGER NOT NULL DEFAULT 0
                        )
                        """);
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_settings (
                          uuid TEXT PRIMARY KEY,
                          chat_filter TEXT NOT NULL DEFAULT 'EVERYONE',
                          notif_filter TEXT NOT NULL DEFAULT 'EVERYONE',
                          night_vision INTEGER NOT NULL DEFAULT 0,
                          money_nametag INTEGER NOT NULL DEFAULT 0,
                          show_money INTEGER NOT NULL DEFAULT 1,
                          show_kills INTEGER NOT NULL DEFAULT 1,
                          show_deaths INTEGER NOT NULL DEFAULT 1,
                          show_playtime INTEGER NOT NULL DEFAULT 1,
                          show_job INTEGER NOT NULL DEFAULT 1,
                          instant_tpa TEXT NOT NULL DEFAULT 'NOBODY',
                          instant_tpahere TEXT NOT NULL DEFAULT 'NOBODY',
                          auction_enabled INTEGER NOT NULL DEFAULT 1,
                          jobs_enabled INTEGER NOT NULL DEFAULT 1,
                          profile_description TEXT NOT NULL DEFAULT ''
                        )
                        """);
                addColumnIfMissing(st, "player_settings", "profile_description",
                        "ALTER TABLE player_settings ADD COLUMN profile_description TEXT NOT NULL DEFAULT ''");
            }
        } catch (ClassNotFoundException | SQLException e) {
            throw new IllegalStateException("Failed to open SQLite database", e);
        }
    }

    private static void addColumnIfMissing(Statement st, String table, String column, String alterSql)
            throws SQLException {
        boolean exists = false;
        try (ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            st.executeUpdate(alterSql);
        }
    }

    public Connection connection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            connect();
        }
        return connection;
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
