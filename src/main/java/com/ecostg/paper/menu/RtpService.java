package com.ecostg.paper.menu;

import com.ecostg.paper.EcoSTGPlugin;
import com.ecostg.paper.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class RtpService {

    private static final int DEFAULT_MAX_RADIUS = 1500;
    private static final int DEFAULT_MIN_RADIUS = 32;
    private static final int DEFAULT_MAX_ATTEMPTS = 80;
    private static final long TIMEOUT_TICKS = 20L * 20;

    private final EcoSTGPlugin plugin;
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
    private final Set<UUID> searching = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> searchToken = new ConcurrentHashMap<>();

    public RtpService(EcoSTGPlugin plugin) {
        this.plugin = plugin;
    }

    public int cooldownSeconds() {
        return Math.max(0, plugin.getConfig().getInt("rtp.cooldown-seconds", 30));
    }

    public long cooldownRemainingMs(UUID uuid) {
        Long until = cooldownUntil.get(uuid);
        if (until == null) {
            return 0;
        }
        return Math.max(0, until - System.currentTimeMillis());
    }

    /**
     * Starts an async RTP search. Returns {@code true} if a search was started
     * (caller should close the dialog). Returns {@code false} on cooldown /
     * already-searching so the menu can stay open.
     */
    public boolean rtp(Player player) {
        UUID uuid = player.getUniqueId();
        long remaining = cooldownRemainingMs(uuid);
        if (remaining > 0) {
            long sec = (remaining + 999) / 1000;
            Messages.send(plugin, player, "<red>RTP cooldown: " + sec + "s remaining.</red>");
            return false;
        }
        if (!searching.add(uuid)) {
            Messages.send(plugin, player, "<red>Already searching for an RTP location.</red>");
            return false;
        }

        int token = searchToken.merge(uuid, 1, Integer::sum);
        Messages.send(plugin, player, "<yellow>Searching for a safe RTP location...</yellow>");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isCurrentSearch(uuid, token)) {
                return;
            }
            finishSearch(uuid, token);
            Player timedOut = Bukkit.getPlayer(uuid);
            if (timedOut != null) {
                Messages.send(plugin, timedOut, "<red>RTP timed out. Try again.</red>");
            }
        }, TIMEOUT_TICKS);

        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline() || !isCurrentSearch(uuid, token)) {
                return;
            }
            Location origin = player.getLocation();
            tryAttempt(uuid, origin.getWorld().getUID(), origin.getBlockX(), origin.getBlockZ(), token, 0);
        }, null);
        return true;
    }

    public boolean rtpQueue(Player player) {
        List<Player> others = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId())) {
                others.add(online);
            }
        }
        if (others.isEmpty()) {
            Messages.send(plugin, player, "<red>No other players online.</red>");
            return false;
        }
        Player target = others.get(ThreadLocalRandom.current().nextInt(others.size()));
        Location dest = target.getLocation().clone();
        String targetName = target.getName();
        player.teleportAsync(dest).thenAccept(ok -> {
            Player now = Bukkit.getPlayer(player.getUniqueId());
            if (now == null) {
                return;
            }
            if (!Boolean.TRUE.equals(ok)) {
                Messages.send(plugin, now, "<red>RTP Queue teleport failed.</red>");
                return;
            }
            Messages.send(plugin, now, "<green>Teleported to " + targetName + ".</green>");
            plugin.logAction(now.getName() + " used RTP Queue to " + targetName);
        });
        return true;
    }

    public void clear(UUID uuid) {
        cooldownUntil.remove(uuid);
        searching.remove(uuid);
        searchToken.remove(uuid);
    }

    private void tryAttempt(UUID uuid, UUID worldId, int originX, int originZ, int token, int attempt) {
        if (!isCurrentSearch(uuid, token)) {
            return;
        }
        int maxAttempts = Math.max(1, plugin.getConfig().getInt("rtp.max-attempts", DEFAULT_MAX_ATTEMPTS));
        if (attempt >= maxAttempts) {
            fail(uuid, token, "<red>Could not find a safe RTP location.</red>");
            return;
        }

        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            fail(uuid, token, "<red>World unloaded during RTP.</red>");
            return;
        }

        boolean allowGenerate = attempt >= Math.max(8, maxAttempts / 2);
        int[] xz = pickCoords(world, originX, originZ, allowGenerate);
        if (xz == null) {
            tryAttempt(uuid, worldId, originX, originZ, token, attempt + 1);
            return;
        }

        int chunkX = xz[0] >> 4;
        int chunkZ = xz[1] >> 4;
        Runnable evaluate = () -> evaluateLoadedChunk(uuid, worldId, originX, originZ, token, attempt, xz[0], xz[1]);

        if (world.isChunkLoaded(chunkX, chunkZ)) {
            plugin.getServer().getRegionScheduler().execute(plugin, world, chunkX, chunkZ, evaluate);
            return;
        }

        world.getChunkAtAsync(chunkX, chunkZ, allowGenerate).thenAccept(chunk -> {
            if (chunk == null) {
                tryAttempt(uuid, worldId, originX, originZ, token, attempt + 1);
                return;
            }
            plugin.getServer().getRegionScheduler().execute(plugin, world, chunkX, chunkZ, evaluate);
        }).exceptionally(ex -> {
            plugin.getLogger().fine("RTP chunk load skipped: " + ex.getMessage());
            tryAttempt(uuid, worldId, originX, originZ, token, attempt + 1);
            return null;
        });
    }

    private void evaluateLoadedChunk(UUID uuid, UUID worldId, int originX, int originZ, int token,
                                     int attempt, int x, int z) {
        if (!isCurrentSearch(uuid, token)) {
            return;
        }
        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            fail(uuid, token, "<red>World unloaded during RTP.</red>");
            return;
        }
        Location loc;
        try {
            loc = findSafeInColumn(world, x, z);
        } catch (Exception e) {
            plugin.getLogger().warning("RTP safety check failed: " + e.getMessage());
            tryAttempt(uuid, worldId, originX, originZ, token, attempt + 1);
            return;
        }
        if (loc == null) {
            tryAttempt(uuid, worldId, originX, originZ, token, attempt + 1);
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            finishSearch(uuid, token);
            return;
        }
        player.teleportAsync(loc).whenComplete((ok, err) -> {
            if (!isCurrentSearch(uuid, token)) {
                return;
            }
            Player now = Bukkit.getPlayer(uuid);
            if (err != null || !Boolean.TRUE.equals(ok)) {
                if (now != null) {
                    plugin.getLogger().warning("RTP teleportAsync failed: "
                            + (err == null ? "false" : err.getMessage()));
                }
                tryAttempt(uuid, worldId, originX, originZ, token, attempt + 1);
                return;
            }
            finishSearch(uuid, token);
            cooldownUntil.put(uuid, System.currentTimeMillis() + cooldownSeconds() * 1000L);
            if (now != null) {
                Messages.send(plugin, now, "<green>Randomly teleported in "
                        + now.getWorld().getName() + ".</green>");
                plugin.logAction(now.getName() + " used RTP in " + now.getWorld().getName());
            }
        });
    }

    private void fail(UUID uuid, int token, String message) {
        finishSearch(uuid, token);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            Messages.send(plugin, player, message);
        }
    }

    private boolean isCurrentSearch(UUID uuid, int token) {
        return searching.contains(uuid) && searchToken.getOrDefault(uuid, 0) == token;
    }

    private void finishSearch(UUID uuid, int token) {
        if (searchToken.getOrDefault(uuid, 0) == token) {
            searching.remove(uuid);
        }
    }

    private int[] pickCoords(World world, int originX, int originZ, boolean allowGenerate) {
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double borderRadius = Math.max(16, border.getSize() / 2.0 - 8);
        double maxRadius = Math.min(borderRadius, maxRadius(world));
        double minRadius = Math.min(maxRadius, Math.max(0,
                plugin.getConfig().getInt("rtp.min-radius", DEFAULT_MIN_RADIUS)));

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int[] fallback = null;
        for (int n = 0; n < 16; n++) {
            double dist;
            if (maxRadius <= minRadius) {
                dist = maxRadius;
            } else {
                dist = Math.sqrt(rng.nextDouble() * (maxRadius * maxRadius - minRadius * minRadius)
                        + minRadius * minRadius);
            }
            double angle = rng.nextDouble() * Math.PI * 2;
            int x = (int) Math.floor(originX + dist * Math.cos(angle));
            int z = (int) Math.floor(originZ + dist * Math.sin(angle));
            x = clamp(x, (int) Math.floor(center.getX() - borderRadius),
                    (int) Math.floor(center.getX() + borderRadius));
            z = clamp(z, (int) Math.floor(center.getZ() - borderRadius),
                    (int) Math.floor(center.getZ() + borderRadius));
            fallback = new int[]{x, z};
            if (allowGenerate || world.isChunkGenerated(x >> 4, z >> 4)) {
                return fallback;
            }
        }
        return allowGenerate ? fallback : null;
    }

    private int maxRadius(World world) {
        int configured = plugin.getConfig().getInt("rtp.max-radius", DEFAULT_MAX_RADIUS);
        if (configured > 0) {
            if (world.getEnvironment() == World.Environment.THE_END) {
                return Math.min(configured, 250);
            }
            return configured;
        }
        return world.getEnvironment() == World.Environment.THE_END ? 160 : DEFAULT_MAX_RADIUS;
    }

    private Location findSafeInColumn(World world, int x, int z) {
        int minY = world.getMinHeight() + 2;
        int maxY = world.getMaxHeight() - 2;

        if (world.getEnvironment() == World.Environment.NETHER) {
            int top = Math.min(120, maxY);
            for (int y = top; y >= 32; y--) {
                if (isSafeStand(world, x, y, z)) {
                    return standing(world, x, y, z);
                }
            }
            return null;
        }

        int surface = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        int motion = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (surface <= minY && motion <= minY) {
            return null;
        }

        int probe = Math.max(surface, motion);
        probe = clamp(probe, minY, maxY);
        Material belowSurface = world.getBlockAt(x, Math.max(minY, probe - 1), z).getType();
        if (isLiquid(belowSurface) || isLiquid(world.getBlockAt(x, probe, z).getType())) {
            return null;
        }

        int hi = clamp(Math.max(surface, motion) + 2, minY, maxY);
        int lo = clamp(Math.min(surface, motion) - 2, minY, maxY);
        for (int y = hi; y >= lo; y--) {
            if (isSafeStand(world, x, y, z)) {
                return standing(world, x, y, z);
            }
        }
        return null;
    }

    private static Location standing(World world, int x, int y, int z) {
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    /**
     * {@code y} is feet. Ground is {@code y - 1}. Tries both heightmap conventions
     * by scanning a small window around the surface in {@link #findSafeInColumn}.
     */
    private boolean isSafeStand(World world, int x, int y, int z) {
        if (y <= world.getMinHeight() + 1 || y >= world.getMaxHeight() - 1) {
            return false;
        }
        Block ground = world.getBlockAt(x, y - 1, z);
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        if (!ground.getType().isSolid() || isDangerous(ground.getType()) || ground.isLiquid()) {
            return false;
        }
        if (!feet.isPassable() || !head.isPassable()) {
            return false;
        }
        if (feet.isLiquid() || head.isLiquid()) {
            return false;
        }
        return !isDangerous(feet.getType()) && !isDangerous(head.getType());
    }

    private static boolean isLiquid(Material type) {
        return type == Material.WATER || type == Material.LAVA
                || type == Material.BUBBLE_COLUMN || type == Material.KELP
                || type == Material.KELP_PLANT || type == Material.SEAGRASS
                || type == Material.TALL_SEAGRASS;
    }

    private static boolean isDangerous(Material type) {
        return type == Material.LAVA
                || type == Material.WATER
                || type == Material.FIRE
                || type == Material.SOUL_FIRE
                || type == Material.MAGMA_BLOCK
                || type == Material.CACTUS
                || type == Material.SWEET_BERRY_BUSH
                || type == Material.WITHER_ROSE
                || type == Material.POWDER_SNOW
                || type == Material.CAMPFIRE
                || type == Material.SOUL_CAMPFIRE;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
