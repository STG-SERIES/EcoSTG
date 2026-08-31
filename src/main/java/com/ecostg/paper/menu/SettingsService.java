package com.ecostg.paper.menu;

import com.ecostg.paper.EcoSTGPlugin;
import com.ecostg.paper.economy.Database;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class SettingsService {

    private static final String TEAM_PREFIX = "ec$";

    private final EcoSTGPlugin plugin;
    private final Database database;

    public SettingsService(EcoSTGPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void ensure(UUID uuid) {
        try (PreparedStatement ps = database.connection().prepareStatement(
                "INSERT OR IGNORE INTO player_settings(uuid) VALUES(?)")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public PlayerSettings get(UUID uuid) {
        ensure(uuid);
        try (PreparedStatement ps = database.connection().prepareStatement(
                "SELECT chat_filter, notif_filter, night_vision, money_nametag, show_money, show_kills, "
                        + "show_deaths, show_playtime, show_job, instant_tpa, instant_tpahere, "
                        + "auction_enabled, jobs_enabled FROM player_settings WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return PlayerSettings.defaults();
                }
                return new PlayerSettings(
                        PlayerSettings.Filter.parse(rs.getString(1)),
                        PlayerSettings.Filter.parse(rs.getString(2)),
                        rs.getInt(3) == 1,
                        rs.getInt(4) == 1,
                        rs.getInt(5) == 1,
                        rs.getInt(6) == 1,
                        rs.getInt(7) == 1,
                        rs.getInt(8) == 1,
                        rs.getInt(9) == 1,
                        PlayerSettings.InstantAllow.parse(rs.getString(10)),
                        PlayerSettings.InstantAllow.parse(rs.getString(11)),
                        rs.getInt(12) == 1,
                        rs.getInt(13) == 1
                );
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void save(UUID uuid, PlayerSettings settings) {
        ensure(uuid);
        try (PreparedStatement ps = database.connection().prepareStatement("""
                UPDATE player_settings SET
                  chat_filter=?, notif_filter=?, night_vision=?, money_nametag=?,
                  show_money=?, show_kills=?, show_deaths=?, show_playtime=?, show_job=?,
                  instant_tpa=?, instant_tpahere=?, auction_enabled=?, jobs_enabled=?
                WHERE uuid=?
                """)) {
            ps.setString(1, settings.chatFilter().name());
            ps.setString(2, settings.notifFilter().name());
            ps.setInt(3, settings.nightVision() ? 1 : 0);
            ps.setInt(4, settings.moneyNametag() ? 1 : 0);
            ps.setInt(5, settings.showMoney() ? 1 : 0);
            ps.setInt(6, settings.showKills() ? 1 : 0);
            ps.setInt(7, settings.showDeaths() ? 1 : 0);
            ps.setInt(8, settings.showPlaytime() ? 1 : 0);
            ps.setInt(9, settings.showJob() ? 1 : 0);
            ps.setString(10, settings.instantTpa().name());
            ps.setString(11, settings.instantTpaHere().name());
            ps.setInt(12, settings.auctionEnabled() ? 1 : 0);
            ps.setInt(13, settings.jobsEnabled() ? 1 : 0);
            ps.setString(14, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void applyVisuals(Player player) {
        PlayerSettings settings = get(player.getUniqueId());
        if (settings.nightVision()) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, true));
        } else {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
        applyMoneyNametag(player, settings.moneyNametag());
    }

    public void applyMoneyNametag(Player player, boolean enabled) {
        Scoreboard board = plugin.getServer().getScoreboardManager().getMainScoreboard();
        String teamName = teamName(player.getUniqueId());
        Team team = board.getTeam(teamName);
        if (!enabled) {
            if (team != null) {
                team.removeEntry(player.getName());
                if (team.getEntries().isEmpty()) {
                    team.unregister();
                }
            }
            return;
        }
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
        double bal = plugin.economy().getBalance(player.getUniqueId());
        String symbol = plugin.getConfig().getString("currency-symbol", "$");
        team.suffix(net.kyori.adventure.text.Component.text(" " + symbol + formatMoney(bal)));
        Objective obj = board.getObjective("ecostg_bal");
        if (obj == null) {
            obj = board.registerNewObjective("ecostg_bal", Criteria.DUMMY,
                    net.kyori.adventure.text.Component.text(symbol));
            obj.setDisplaySlot(DisplaySlot.BELOW_NAME);
        }
        obj.getScore(player.getName()).setScore((int) Math.min(Integer.MAX_VALUE, Math.max(0, Math.round(bal))));
    }

    public void refreshMoneyNametag(Player player) {
        PlayerSettings settings = get(player.getUniqueId());
        if (settings.moneyNametag()) {
            applyMoneyNametag(player, true);
        }
    }

    public void clearVisuals(Player player) {
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        applyMoneyNametag(player, false);
    }

    private static String teamName(UUID uuid) {
        String raw = TEAM_PREFIX + uuid.toString().replace("-", "");
        return raw.substring(0, Math.min(16, raw.length()));
    }

    private static String formatMoney(double amount) {
        if (amount >= 1_000_000) {
            return String.format(java.util.Locale.US, "%.1fM", amount / 1_000_000.0);
        }
        if (amount >= 1_000) {
            return String.format(java.util.Locale.US, "%.1fK", amount / 1_000.0);
        }
        return String.format(java.util.Locale.US, "%.0f", amount);
    }
}
