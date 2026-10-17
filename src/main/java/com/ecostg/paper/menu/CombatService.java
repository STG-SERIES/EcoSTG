package com.ecostg.paper.menu;

import com.ecostg.paper.EcoSTGPlugin;
import com.ecostg.paper.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combat tag: blocks teleport features while active.
 * OPs are never tagged; the other player in a fight with an OP still is.
 */
public final class CombatService implements Listener {

    private static final int DEFAULT_DURATION_SECONDS = 15;

    private final EcoSTGPlugin plugin;
    private final Map<UUID, Long> combatUntilMs = new ConcurrentHashMap<>();
    private BukkitTask displayTask;

    public CombatService(EcoSTGPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        displayTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickDisplay, 20L, 20L);
    }

    public void stop() {
        if (displayTask != null) {
            displayTask.cancel();
            displayTask = null;
        }
        combatUntilMs.clear();
    }

    public int durationSeconds() {
        return Math.max(1, plugin.getConfig().getInt("combat.duration-seconds", DEFAULT_DURATION_SECONDS));
    }

    public boolean isInCombat(UUID uuid) {
        if (!plugin.features().combat()) {
            return false;
        }
        return remainingSeconds(uuid) > 0;
    }

    public long remainingSeconds(UUID uuid) {
        Long until = combatUntilMs.get(uuid);
        if (until == null) {
            return 0;
        }
        long ms = until - System.currentTimeMillis();
        if (ms <= 0) {
            combatUntilMs.remove(uuid);
            return 0;
        }
        return (ms + 999L) / 1000L;
    }

    /**
     * @return {@code true} if the player is in combat (and was told they cannot teleport)
     */
    public boolean denyIfInCombat(Player player) {
        if (!plugin.features().combat()) {
            return false;
        }
        long sec = remainingSeconds(player.getUniqueId());
        if (sec <= 0) {
            return false;
        }
        Messages.send(plugin, player, "<red>You cannot teleport while in combat (" + sec + "s).</red>");
        return true;
    }

    public void clear(UUID uuid) {
        combatUntilMs.remove(uuid);
    }

    private void tag(Player player) {
        if (!plugin.features().combat()) {
            return;
        }
        if (player == null || !player.isOnline() || player.isOp()) {
            return;
        }
        combatUntilMs.put(player.getUniqueId(),
                System.currentTimeMillis() + durationSeconds() * 1000L);
        sendBar(player);
    }

    private void sendBar(Player player) {
        long sec = remainingSeconds(player.getUniqueId());
        if (sec <= 0) {
            return;
        }
        player.sendActionBar(Messages.mm("Combat: <blue>" + sec + "s</blue>"));
    }

    private void tickDisplay() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = combatUntilMs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (entry.getValue() <= now) {
                it.remove();
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                it.remove();
                continue;
            }
            sendBar(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCombat(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (event.getFinalDamage() <= 0) {
            return;
        }
        Player attacker = resolveAttackingPlayer(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        // PvP only — mobs, fall, fire, etc. do not tag.
        tag(victim);
        tag(attacker);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        clear(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    private static Player resolveAttackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        if (damager instanceof TNTPrimed tnt) {
            Entity source = tnt.getSource();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
