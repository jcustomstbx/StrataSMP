package com.stratasmp.stratahub;

import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

/**
 * Keeps hostile mobs out of the SMP safe zone (the boxes in {@code safe-zone-outlines}): they can't target or
 * hurt anyone inside it, and one that wanders in is moved back out to the nearest edge. Spawning inside is
 * already denied by the zone's WorldGuard flags; this covers mobs that walk in from outside.
 */
final class SafeZoneMobGuard extends BukkitRunnable implements Listener {

    private static final double EXIT_MARGIN = 2.0;

    private final StrataHub plugin;

    SafeZoneMobGuard(StrataHub plugin) {
        this.plugin = plugin;
    }

    void start() {
        runTaskTimer(plugin, 40L, 10L);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("safe-zone-mob-guard", true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!enabled() || !(event.getEntity() instanceof Enemy) || !(event.getTarget() instanceof Player target)) {
            return;
        }
        if (insideAnyZone(target.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled() || !(event.getEntity() instanceof Player victim) || !insideAnyZone(victim.getLocation())) {
            return;
        }
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Entity shooterEntity) {
                source = shooterEntity;
            }
        }
        if (source instanceof Enemy) {
            event.setCancelled(true);
        }
    }

    @Override
    public void run() {
        if (!enabled()) {
            return;
        }
        for (Map<?, ?> zone : plugin.getConfig().getMapList("safe-zone-outlines")) {
            try {
                sweep(zone);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Bad safe-zone-outlines entry " + zone + ": " + e);
            }
        }
    }

    private void sweep(Map<?, ?> zone) {
        World world = Bukkit.getWorld(String.valueOf(zone.get("world")));
        if (world == null) {
            return;
        }
        double x0 = Math.min(number(zone, "min-x"), number(zone, "max-x"));
        double x1 = Math.max(number(zone, "min-x"), number(zone, "max-x")) + 1;
        double z0 = Math.min(number(zone, "min-z"), number(zone, "max-z"));
        double z1 = Math.max(number(zone, "min-z"), number(zone, "max-z")) + 1;
        if (!world.isChunkLoaded((int) Math.floor((x0 + x1) / 2) >> 4, (int) Math.floor((z0 + z1) / 2) >> 4)) {
            return;
        }
        BoundingBox box = new BoundingBox(x0, world.getMinHeight(), z0, x1, world.getMaxHeight(), z1);
        for (Entity entity : world.getNearbyEntities(box)) {
            if (entity instanceof Player || !(entity instanceof Enemy) || !(entity instanceof LivingEntity living)) {
                continue;
            }
            Location loc = living.getLocation();
            // nearest edge, then a couple of blocks beyond it so it doesn't step straight back in
            double toWest = loc.getX() - x0, toEast = x1 - loc.getX(), toNorth = loc.getZ() - z0, toSouth = z1 - loc.getZ();
            double best = Math.min(Math.min(toWest, toEast), Math.min(toNorth, toSouth));
            double outX = loc.getX(), outZ = loc.getZ();
            if (best == toWest) {
                outX = x0 - EXIT_MARGIN;
            } else if (best == toEast) {
                outX = x1 + EXIT_MARGIN;
            } else if (best == toNorth) {
                outZ = z0 - EXIT_MARGIN;
            } else {
                outZ = z1 + EXIT_MARGIN;
            }
            int y = world.getHighestBlockYAt((int) Math.floor(outX), (int) Math.floor(outZ)) + 1;
            if (living instanceof Mob mob) {
                mob.setTarget(null);
            }
            living.teleport(new Location(world, outX, y, outZ, loc.getYaw(), loc.getPitch()));
        }
    }

    private boolean insideAnyZone(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        for (Map<?, ?> zone : plugin.getConfig().getMapList("safe-zone-outlines")) {
            try {
                World world = Bukkit.getWorld(String.valueOf(zone.get("world")));
                if (world == null || !world.equals(loc.getWorld())) {
                    continue;
                }
                int minX = number(zone, "min-x");
                int maxX = number(zone, "max-x");
                int minZ = number(zone, "min-z");
                int maxZ = number(zone, "max-z");
                if (loc.getX() >= Math.min(minX, maxX) && loc.getX() <= Math.max(minX, maxX) + 1
                    && loc.getZ() >= Math.min(minZ, maxZ) && loc.getZ() <= Math.max(minZ, maxZ) + 1) {
                    return true;
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Bad safe-zone-outlines entry " + zone + ": " + e);
            }
        }
        return false;
    }

    private static int number(Map<?, ?> zone, String key) {
        Object value = zone.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("missing number '" + key + "'");
        }
        return number.intValue();
    }
}
