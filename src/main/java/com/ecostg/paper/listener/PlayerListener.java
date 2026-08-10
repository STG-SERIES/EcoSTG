package com.ecostg.paper.listener;

import com.ecostg.paper.EcoSTGPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {

    private final EcoSTGPlugin plugin;

    public PlayerListener(EcoSTGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.economy().ensurePlayer(event.getPlayer());
        plugin.logAction(event.getPlayer().getName() + " joined (balance "
                + plugin.economy().getBalance(event.getPlayer().getUniqueId()) + ")");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.chatInput().clear(event.getPlayer());
        plugin.guis().clear(event.getPlayer());
    }
}
