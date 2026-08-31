package com.ecostg.paper.job;

import com.ecostg.paper.EcoSTGPlugin;
import com.ecostg.paper.economy.Database;
import com.ecostg.paper.economy.EconomyService;
import com.ecostg.paper.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class JobService {

    public record PlayerJob(
            String jobId,
            Long startDay,
            Long deadlineDay,
            long cooldownUntil
    ) {
        public boolean hasJob() {
            return jobId != null && !jobId.isBlank();
        }
    }

    private final EcoSTGPlugin plugin;
    private final Database database;
    private final Map<String, JobDefinition> jobs = new LinkedHashMap<>();
    private BukkitTask task;

    public JobService(EcoSTGPlugin plugin, Database database, EconomyService economy) {
        this.plugin = plugin;
        this.database = database;
        loadJobs();
    }

    private void loadJobs() {
        File file = new File(plugin.getDataFolder(), "jobs.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("jobs");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection job = section.getConfigurationSection(id);
            if (job == null) {
                continue;
            }
            Material icon = Material.matchMaterial(job.getString("icon", "PAPER"));
            Material required = Material.matchMaterial(job.getString("required-material", "STONE"));
            if (icon == null || required == null) {
                continue;
            }
            jobs.put(id, new JobDefinition(
                    id,
                    job.getString("display-name", id),
                    icon,
                    job.getStringList("description"),
                    required,
                    job.getInt("required-amount", 1)
            ));
        }
        plugin.getLogger().info("Loaded " + jobs.size() + " jobs.");
    }

    public Map<String, JobDefinition> jobs() {
        return jobs;
    }

    public JobDefinition get(String id) {
        return jobs.get(id);
    }

    public long currentWorldDay() {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        if (world == null) {
            return 0;
        }
        return world.getFullTime() / 24000L;
    }

    public PlayerJob getPlayerJob(UUID uuid) {
        try (PreparedStatement ps = database.connection().prepareStatement(
                "SELECT job_id, job_start_day, job_deadline_day, job_cooldown_until FROM players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new PlayerJob(null, null, null, 0);
                }
                String jobId = rs.getString(1);
                long start = rs.getLong(2);
                boolean startNull = rs.wasNull();
                long deadline = rs.getLong(3);
                boolean deadlineNull = rs.wasNull();
                return new PlayerJob(
                        jobId,
                        startNull ? null : start,
                        deadlineNull ? null : deadline,
                        rs.getLong(4)
                );
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean isOnCooldown(UUID uuid) {
        return getPlayerJob(uuid).cooldownUntil() > System.currentTimeMillis();
    }

    public long cooldownRemainingMs(UUID uuid) {
        return Math.max(0, getPlayerJob(uuid).cooldownUntil() - System.currentTimeMillis());
    }

    public boolean isWorker(UUID uuid) {
        return getPlayerJob(uuid).hasJob();
    }

    public boolean joinJob(Player player, String jobId) {
        JobDefinition def = jobs.get(jobId);
        if (def == null) {
            return false;
        }
        PlayerJob current = getPlayerJob(player.getUniqueId());
        if (current.hasJob()) {
            return false;
        }
        if (isOnCooldown(player.getUniqueId())) {
            return false;
        }
        int days = plugin.getConfig().getInt("jobs.deadline-ingame-days", 4);
        long day = currentWorldDay();
        try (PreparedStatement ps = database.connection().prepareStatement(
                "UPDATE players SET job_id=?, job_start_day=?, job_deadline_day=?, job_cooldown_until=0 WHERE uuid=?")) {
            ps.setString(1, jobId);
            ps.setLong(2, day);
            ps.setLong(3, day + days);
            ps.setString(4, player.getUniqueId().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        plugin.logAction(player.getName() + " joined job " + jobId + " (deadline day " + (day + days) + ")");
        return true;
    }

    public void fire(UUID uuid, String reason) {
        PlayerJob job = getPlayerJob(uuid);
        if (!job.hasJob()) {
            return;
        }
        int cooldownDays = plugin.getConfig().getInt("jobs.fire-cooldown-real-days", 30);
        long until = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(cooldownDays);
        try (PreparedStatement ps = database.connection().prepareStatement(
                "UPDATE players SET job_id=NULL, job_start_day=NULL, job_deadline_day=NULL, job_cooldown_until=? WHERE uuid=?")) {
            ps.setLong(1, until);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        String name = plugin.economy().getName(uuid);
        plugin.logAction(name + " was fired from job " + job.jobId() + " (" + reason + ")");
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            Messages.send(plugin, online, "<red>You were fired from your job: " + reason + "</red>");
        }
    }

    public void resetCooldown(UUID uuid) {
        try (PreparedStatement ps = database.connection().prepareStatement(
                "UPDATE players SET job_cooldown_until=0 WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Admin cancel: removes the job without applying the fired cooldown. */
    public boolean cancelJob(UUID uuid) {
        PlayerJob job = getPlayerJob(uuid);
        if (!job.hasJob()) {
            return false;
        }
        try (PreparedStatement ps = database.connection().prepareStatement(
                "UPDATE players SET job_id=NULL, job_start_day=NULL, job_deadline_day=NULL WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        plugin.logAction(plugin.economy().getName(uuid) + " job cancelled (was " + job.jobId() + ")");
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            Messages.send(plugin, online, "<yellow>Your job was cancelled by an admin.</yellow>");
        }
        return true;
    }

    public record ActiveJobEntry(UUID uuid, String name, String jobId, Long startDay, Long deadlineDay) {
    }

    public List<ActiveJobEntry> listActiveJobs() {
        List<ActiveJobEntry> list = new ArrayList<>();
        try (PreparedStatement ps = database.connection().prepareStatement(
                "SELECT uuid, name, job_id, job_start_day, job_deadline_day FROM players WHERE job_id IS NOT NULL ORDER BY name COLLATE NOCASE");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long start = rs.getLong(4);
                boolean startNull = rs.wasNull();
                long deadline = rs.getLong(5);
                boolean deadlineNull = rs.wasNull();
                list.add(new ActiveJobEntry(
                        UUID.fromString(rs.getString(1)),
                        rs.getString(2),
                        rs.getString(3),
                        startNull ? null : start,
                        deadlineNull ? null : deadline
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return list;
    }

    public long daysUntilDeadline(PlayerJob job) {
        if (job.deadlineDay() == null) {
            return 0;
        }
        return Math.max(0, job.deadlineDay() - currentWorldDay());
    }

    /**
     * Takes the required job items from the player without refreshing the deadline.
     * Used by /jobsell so items can be listed on the AH with a worker badge.
     */
    public ItemStack takeDeliveryItems(Player player) {
        PlayerJob job = getPlayerJob(player.getUniqueId());
        if (!job.hasJob()) {
            return null;
        }
        JobDefinition def = jobs.get(job.jobId());
        if (def == null) {
            return null;
        }
        if (!removeItems(player, def.requiredMaterial(), def.requiredAmount())) {
            return null;
        }
        return new ItemStack(def.requiredMaterial(), def.requiredAmount());
    }

    public void refreshDeadline(Player player) {
        PlayerJob job = getPlayerJob(player.getUniqueId());
        if (!job.hasJob()) {
            return;
        }
        int days = plugin.getConfig().getInt("jobs.deadline-ingame-days", 4);
        long day = currentWorldDay();
        try (PreparedStatement ps = database.connection().prepareStatement(
                "UPDATE players SET job_start_day=?, job_deadline_day=? WHERE uuid=?")) {
            ps.setLong(1, day);
            ps.setLong(2, day + days);
            ps.setString(3, player.getUniqueId().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        plugin.logAction(player.getName() + " refreshed job deadline for " + job.jobId()
                + " (deadline day " + (day + days) + ")");
    }

    @Deprecated
    public boolean deliver(Player player) {
        ItemStack taken = takeDeliveryItems(player);
        if (taken == null) {
            return false;
        }
        refreshDeadline(player);
        return true;
    }

    private boolean removeItems(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType() != material) {
                continue;
            }
            remaining -= stack.getAmount();
        }
        if (remaining > 0) {
            return false;
        }
        remaining = amount;
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int take = Math.min(stack.getAmount(), remaining);
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                contents[i] = null;
            }
            remaining -= take;
            if (remaining <= 0) {
                break;
            }
        }
        player.getInventory().setStorageContents(contents);
        return true;
    }

    public void startScheduler() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::checkDeadlines, 200L, 200L);
    }

    public void stopScheduler() {
        if (task != null) {
            task.cancel();
        }
    }

    private void checkDeadlines() {
        long day = currentWorldDay();
        List<UUID> toFire = new ArrayList<>();
        try (PreparedStatement ps = database.connection().prepareStatement(
                "SELECT uuid, job_id, job_deadline_day FROM players WHERE job_id IS NOT NULL");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long deadline = rs.getLong(3);
                if (!rs.wasNull() && day > deadline) {
                    toFire.add(UUID.fromString(rs.getString(1)));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Job deadline check failed: " + e.getMessage());
            return;
        }
        for (UUID uuid : toFire) {
            fire(uuid, "missed delivery deadline");
        }
    }
}
