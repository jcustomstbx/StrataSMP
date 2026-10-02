package com.stratasmp.stratahub;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

/**
 * Two RankEssentials combat-tag quirks around the SMP safe zone (the same box drawn by
 * {@link SafeZoneOutline}, read from {@code safe-zone-outlines}):
 * <ul>
 *   <li>RankEssentials tags on the raw PvP hit event, not on damage actually landing - so a hit that the
 *   safe zone reduces to 0 (its own {@code invincible: allow} flag) still starts a combat timer. Any hit
 *   that lands at 0 final damage while either player is in the zone gets untagged again right after.</li>
 *   <li>A tagged player can otherwise just walk or teleport into the zone and be untouchable mid-fight.
 *   Crossing in from outside while in combat is bounced back with a shove, like running into a wall.</li>
 * </ul>
 */
public final class SafeZoneCombatGuard implements Listener {

    private static final long NOTICE_COOLDOWN_MILLIS = 2000L;

    private final StrataHub plugin;
    private final Map<UUID, Long> lastNoticeAt = new HashMap<>();
    private Method isInCombat;
    private Method untag;
    private boolean broken;

    public SafeZoneCombatGuard(StrataHub plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!event.isCancelled() && event.getFinalDamage() > 0.0) {
            return;
        }
        Player damager = resolvePlayer(event.getDamager());
        boolean inZone = insideAnyZone(victim.getLocation()) || (damager != null && insideAnyZone(damager.getLocation()));
        if (!inZone) {
            return;
        }
        untag(victim.getUniqueId());
        if (damager != null) {
            untag(damager.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || !insideAnyZone(to) || insideAnyZone(from)) {
            return;
        }
        Player player = event.getPlayer();
        if (!isInCombat(player.getUniqueId())) {
            return;
        }
        event.setTo(from);
        Vector push = from.toVector().subtract(to.toVector());
        push.setY(0);
        if (push.lengthSquared() > 1.0e-6) {
            // horizontal only, and the player's own vertical speed is kept: an upward kick here let a
            // tagged player hold forward against the edge and be launched over and over, i.e. fly
            push.normalize().multiply(0.6);
            Vector velocity = player.getVelocity();
            velocity.setX(push.getX());
            velocity.setZ(push.getZ());
            player.setVelocity(velocity);
        }
        notify(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || !insideAnyZone(to) || (from != null && insideAnyZone(from))) {
            return;
        }
        if (!isInCombat(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        notify(event.getPlayer());
    }

    private void notify(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastNoticeAt.get(player.getUniqueId());
        if (last != null && now - last < NOTICE_COOLDOWN_MILLIS) {
            return;
        }
        lastNoticeAt.put(player.getUniqueId(), now);
        player.sendActionBar(Component.text("You can't enter the safe zone while in combat!", NamedTextColor.RED));
    }

    private boolean insideAnyZone(Location loc) {
        if (loc == null || loc.getWorld() == null || !plugin.getConfig().getBoolean("safe-zone-combat-guard", true)) {
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
                double x = loc.getX();
                double z = loc.getZ();
                if (x >= Math.min(minX, maxX) && x <= Math.max(minX, maxX) + 1
                    && z >= Math.min(minZ, maxZ) && z <= Math.max(minZ, maxZ) + 1) {
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

    private boolean isInCombat(UUID uuid) {
        Object manager = combatManager();
        if (manager == null) {
            return false;
        }
        try {
            return (boolean) isInCombat.invoke(manager, uuid);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            plugin.getLogger().warning("Couldn't read RankEssentials' combat state, leaving the safe zone open: " + e);
            return false;
        }
    }

    private void untag(UUID uuid) {
        Object manager = combatManager();
        if (manager == null) {
            return;
        }
        try {
            untag.invoke(manager, uuid);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            plugin.getLogger().warning("Couldn't clear RankEssentials' combat tag for a safe-zone hit: " + e);
        }
    }

    private Object combatManager() {
        if (broken) {
            return null;
        }
        Plugin rankEssentials = plugin.getServer().getPluginManager().getPlugin("RankEssentials");
        if (rankEssentials == null || !rankEssentials.isEnabled()) {
            return null;
        }
        try {
            Object manager = rankEssentials.getClass().getMethod("getCombatManager").invoke(rankEssentials);
            if (manager != null && isInCombat == null) {
                isInCombat = manager.getClass().getMethod("isInCombat", UUID.class);
                untag = manager.getClass().getMethod("untag", UUID.class);
            }
            return manager;
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            plugin.getLogger().warning("Couldn't reach RankEssentials' CombatManager, safe-zone combat rules are off: " + e);
            return null;
        }
    }

    private static Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
