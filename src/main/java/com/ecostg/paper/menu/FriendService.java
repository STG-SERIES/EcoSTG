package com.ecostg.paper.menu;

import com.ecostg.paper.EcoSTGPlugin;
import com.ecostg.paper.economy.Database;
import com.ecostg.paper.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class FriendService {

    public enum FollowResult {
        INVALID,
        ALREADY_FOLLOWING,
        FOLLOWED,
        NOW_FRIENDS
    }

    public record FriendEntry(UUID uuid, String name) {
    }

    private final EcoSTGPlugin plugin;
    private final Database database;

    public FriendService(EcoSTGPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public boolean areFriends(UUID a, UUID b) {
        if (a.equals(b)) {
            return true;
        }
        return isFollowing(a, b) && isFollowing(b, a);
    }

    public boolean isFollowing(UUID from, UUID to) {
        try (PreparedStatement ps = database.connection().prepareStatement(
                "SELECT 1 FROM friends WHERE uuid=? AND friend_uuid=?")) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public FollowResult follow(UUID from, UUID to) {
        if (from.equals(to)) {
            return FollowResult.INVALID;
        }
        if (isFollowing(from, to)) {
            return FollowResult.ALREADY_FOLLOWING;
        }
        try (PreparedStatement ps = database.connection().prepareStatement(
                "INSERT OR IGNORE INTO friends(uuid, friend_uuid) VALUES(?,?)")) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }

        String fromName = plugin.economy().getName(from);
        String toName = plugin.economy().getName(to);
        Player fromPlayer = Bukkit.getPlayer(from);
        Player toPlayer = Bukkit.getPlayer(to);
        boolean mutual = isFollowing(to, from);
        if (mutual) {
            plugin.logAction(fromName + " followed back " + toName + " (now friends)");
            if (fromPlayer != null) {
                Messages.send(plugin, fromPlayer, "<green>You and " + toName + " are now friends.</green>");
            }
            if (toPlayer != null) {
                Messages.send(plugin, toPlayer, "<green>" + fromName + " followed you back. You are now friends.</green>");
            }
            return FollowResult.NOW_FRIENDS;
        }
        plugin.logAction(fromName + " followed " + toName);
        if (fromPlayer != null) {
            Messages.send(plugin, fromPlayer, "<green>You followed " + toName
                    + ".</green> <gray>You become friends when they follow you back.</gray>");
        }
        if (toPlayer != null) {
            Messages.send(plugin, toPlayer, "<gold>" + fromName
                    + " followed you.</gold> <gray>Follow them back to become friends.</gray>");
        }
        return FollowResult.FOLLOWED;
    }

    public boolean unfollow(UUID from, UUID to) {
        boolean wereFriends = areFriends(from, to);
        try (PreparedStatement ps = database.connection().prepareStatement(
                "DELETE FROM friends WHERE uuid=? AND friend_uuid=?")) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            boolean ok = ps.executeUpdate() > 0;
            if (ok) {
                String fromName = plugin.economy().getName(from);
                String toName = plugin.economy().getName(to);
                plugin.logAction(fromName + " unfollowed " + toName);
                Player fromPlayer = Bukkit.getPlayer(from);
                Player toPlayer = Bukkit.getPlayer(to);
                if (fromPlayer != null) {
                    Messages.send(plugin, fromPlayer, wereFriends
                            ? "<yellow>Unfollowed " + toName + ". You are no longer friends.</yellow>"
                            : "<yellow>Unfollowed " + toName + ".</yellow>");
                }
                if (wereFriends && toPlayer != null) {
                    Messages.send(plugin, toPlayer, "<yellow>" + fromName
                            + " unfollowed you. You are no longer friends.</yellow>");
                }
            }
            return ok;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<FriendEntry> listFollowing(UUID owner) {
        return query("SELECT friend_uuid FROM friends WHERE uuid=?", owner);
    }

    public List<FriendEntry> listFollowers(UUID owner) {
        return query("SELECT uuid FROM friends WHERE friend_uuid=?", owner);
    }

    public List<FriendEntry> listFriends(UUID owner) {
        Set<UUID> following = listFollowing(owner).stream().map(FriendEntry::uuid).collect(Collectors.toSet());
        List<FriendEntry> friends = new ArrayList<>();
        for (FriendEntry follower : listFollowers(owner)) {
            if (following.contains(follower.uuid())) {
                friends.add(follower);
            }
        }
        friends.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return friends;
    }

    public List<FriendEntry> listPendingFollowing(UUID owner) {
        Set<UUID> friends = listFriends(owner).stream().map(FriendEntry::uuid).collect(Collectors.toSet());
        List<FriendEntry> pending = new ArrayList<>();
        for (FriendEntry entry : listFollowing(owner)) {
            if (!friends.contains(entry.uuid())) {
                pending.add(entry);
            }
        }
        return pending;
    }

    public List<FriendEntry> listPendingFollowers(UUID owner) {
        Set<UUID> following = listFollowing(owner).stream().map(FriendEntry::uuid).collect(Collectors.toSet());
        List<FriendEntry> pending = new ArrayList<>();
        for (FriendEntry entry : listFollowers(owner)) {
            if (!following.contains(entry.uuid())) {
                pending.add(entry);
            }
        }
        return pending;
    }

    private List<FriendEntry> query(String sql, UUID bind) {
        List<FriendEntry> list = new ArrayList<>();
        try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
            ps.setString(1, bind.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString(1));
                    list.add(new FriendEntry(id, plugin.economy().getName(id)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        list.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return list;
    }
}
