package com.ecostg.paper.menu;

import com.ecostg.paper.EcoSTGPlugin;
import com.ecostg.paper.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TpaService {

    public enum Type {
        TPA,
        TPA_HERE
    }

    public record Pending(UUID from, UUID to, Type type) {
    }

    private final EcoSTGPlugin plugin;
    private final Map<UUID, Pending> pendingByTarget = new ConcurrentHashMap<>();

    public TpaService(EcoSTGPlugin plugin) {
        this.plugin = plugin;
    }

    public void request(Player from, Player to, Type type) {
        if (from.getUniqueId().equals(to.getUniqueId())) {
            Messages.send(plugin, from, "<red>You cannot teleport to yourself.</red>");
            return;
        }

        PlayerSettings targetSettings = plugin.settings().get(to.getUniqueId());
        PlayerSettings.InstantAllow allow = type == Type.TPA
                ? targetSettings.instantTpa()
                : targetSettings.instantTpaHere();
        boolean friends = plugin.friends().areFriends(from.getUniqueId(), to.getUniqueId());
        boolean instant = switch (allow) {
            case ANYONE -> true;
            case FRIENDS -> friends;
            case NOBODY -> false;
        };
        if (instant) {
            perform(from, to, type);
            Messages.send(plugin, from, "<green>Teleport completed (instant privacy).</green>");
            Messages.send(plugin, to, "<green>" + from.getName() + " teleported via instant privacy.</green>");
            return;
        }

        pendingByTarget.put(to.getUniqueId(), new Pending(from.getUniqueId(), to.getUniqueId(), type));
        String label = type == Type.TPA ? "TPA" : "TPAHere";
        Messages.send(plugin, from, "<yellow>" + label + " request sent to " + to.getName() + ".</yellow>");
        Messages.send(plugin, to, "<gold>" + from.getName() + " requested " + label + ".</gold>");
        to.sendMessage(Component.text("[Accept]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/tpaccept"))
                .append(Component.text("  "))
                .append(Component.text("[Deny]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/tpdeny"))));
        plugin.menus().openTpaPrompt(to, from, type);
        plugin.logAction(from.getName() + " sent " + label + " to " + to.getName());
    }

    public boolean accept(Player target) {
        Pending pending = pendingByTarget.remove(target.getUniqueId());
        if (pending == null) {
            Messages.send(plugin, target, "<red>No pending teleport request.</red>");
            return false;
        }
        Player from = Bukkit.getPlayer(pending.from());
        if (from == null || !from.isOnline()) {
            Messages.send(plugin, target, "<red>That player is no longer online.</red>");
            return false;
        }
        perform(from, target, pending.type());
        Messages.send(plugin, target, "<green>Teleport accepted.</green>");
        Messages.send(plugin, from, "<green>" + target.getName() + " accepted your teleport request.</green>");
        plugin.logAction(target.getName() + " accepted teleport from " + from.getName());
        return true;
    }

    public boolean deny(Player target) {
        Pending pending = pendingByTarget.remove(target.getUniqueId());
        if (pending == null) {
            Messages.send(plugin, target, "<red>No pending teleport request.</red>");
            return false;
        }
        Player from = Bukkit.getPlayer(pending.from());
        Messages.send(plugin, target, "<yellow>Teleport denied.</yellow>");
        if (from != null) {
            Messages.send(plugin, from, "<red>" + target.getName() + " denied your teleport request.</red>");
        }
        return true;
    }

    public void clear(UUID player) {
        pendingByTarget.remove(player);
        pendingByTarget.entrySet().removeIf(e -> e.getValue().from().equals(player));
    }

    private void perform(Player from, Player to, Type type) {
        if (type == Type.TPA) {
            from.teleport(to.getLocation());
        } else {
            to.teleport(from.getLocation());
        }
    }
}
