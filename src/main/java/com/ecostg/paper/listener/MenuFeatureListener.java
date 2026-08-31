package com.ecostg.paper.listener;

import com.ecostg.paper.EcoSTGBootstrap;
import com.ecostg.paper.EcoSTGPlugin;
import com.ecostg.paper.menu.PlayerSettings;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MenuFeatureListener implements Listener {

    private final EcoSTGPlugin plugin;

    public MenuFeatureListener(EcoSTGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key id = event.getIdentifier();
        if (!id.namespace().equals("ecostg")) {
            return;
        }
        boolean hubClick = id.equals(EcoSTGBootstrap.OPEN_MAIN)
                || id.value().startsWith("hub_");
        if (!hubClick) {
            return;
        }
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) {
            return;
        }
        Player player = connection.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (id.equals(EcoSTGBootstrap.OPEN_MAIN)) {
                plugin.menus().openMain(player);
            } else {
                plugin.menus().handleRegistryHubAction(player, id);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.economy().ensurePlayer(player);
        plugin.stats().onJoin(player.getUniqueId());
        plugin.settings().ensure(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.settings().applyVisuals(player), 20L);
        plugin.logAction(player.getName() + " joined (balance "
                + plugin.economy().getBalance(player.getUniqueId()) + ")");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.stats().onQuit(player.getUniqueId());
        plugin.tpa().clear(player.getUniqueId());
        plugin.rtp().clear(player.getUniqueId());
        plugin.settings().clearVisuals(player);
        plugin.chatInput().clear(player);
        plugin.guis().clear(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        plugin.stats().addBlockPlace(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        plugin.stats().addBlockBreak(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (entity instanceof Player victim) {
            plugin.stats().addDeath(victim.getUniqueId());
            if (killer != null) {
                plugin.stats().addKill(killer.getUniqueId());
            }
            return;
        }
        if (killer != null) {
            plugin.stats().addMobKill(killer.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        ComponentOrNull deathMessage = new ComponentOrNull(event.deathMessage());
        if (deathMessage.value() == null) {
            return;
        }
        event.deathMessage(null);

        Set<Audience> recipients = new HashSet<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (shouldSeeDeath(viewer, victim, killer)) {
                recipients.add(viewer);
            }
        }
        for (Audience audience : recipients) {
            audience.sendMessage(deathMessage.value());
        }
    }

    private boolean shouldSeeDeath(Player viewer, Player victim, Player killer) {
        UUID viewerId = viewer.getUniqueId();
        if (viewerId.equals(victim.getUniqueId()) || (killer != null && viewerId.equals(killer.getUniqueId()))) {
            return true;
        }
        PlayerSettings settings = plugin.settings().get(viewerId);
        if (settings.notifFilter() == PlayerSettings.Filter.EVERYONE) {
            return true;
        }
        boolean friendVictim = plugin.friends().areFriends(viewerId, victim.getUniqueId());
        boolean friendKiller = killer != null && plugin.friends().areFriends(viewerId, killer.getUniqueId());
        return friendVictim || friendKiller;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        UUID senderId = sender.getUniqueId();
        PlayerSettings senderSettings = plugin.settings().get(senderId);

        event.viewers().removeIf(audience -> {
            if (!(audience instanceof Player viewer)) {
                return false;
            }
            UUID viewerId = viewer.getUniqueId();
            if (viewerId.equals(senderId)) {
                return false;
            }
            PlayerSettings viewerSettings = plugin.settings().get(viewerId);
            if (senderSettings.chatFilter() == PlayerSettings.Filter.FRIENDS
                    && !plugin.friends().areFriends(senderId, viewerId)) {
                return true;
            }
            if (viewerSettings.chatFilter() == PlayerSettings.Filter.FRIENDS
                    && !plugin.friends().areFriends(viewerId, senderId)) {
                return true;
            }
            return false;
        });
    }

    private record ComponentOrNull(net.kyori.adventure.text.Component value) {
    }
}
