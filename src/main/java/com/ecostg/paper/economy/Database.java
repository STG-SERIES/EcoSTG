package com.ecostg.paper.economy;

import com.ecostg.paper.EcoSTGPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
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
                          item_bytes BLOB NOT NULL,
                          created_at INTEGER NOT NULL
                        )
                        """);
            }
        } catch (ClassNotFoundException | SQLException e) {
            throw new IllegalStateException("Failed to open SQLite database", e);
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
