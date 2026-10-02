package com.stratasmp.stratahub;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * A minecraft:barrier is supposed to be a hard wall, full stop. Vanilla collision already stops plain
 * walking into one, but a single fast step - an ender pearl landing, an elytra glide, another plugin's
 * teleport - can carry a player clean across a one-block-thick wall between two ticks, since nothing
 * checks the space in between. This traces the straight line from where a player was to where they're
 * about to land and, if a barrier sits anywhere on that line, undoes the move instead of letting it
 * through. Applies everywhere on the server, to everyone including ops - a barrier that only sometimes
 * works isn't a wall.
 */
final class BarrierNoClip implements Listener {

    // below this, no single step could possibly skip a whole block - normal walking never needs the check
    private static final double MOVE_CHECK_DISTANCE = 1.05;
    private static final double LANDING_PULLBACK = 0.3;
    private static final long NOTICE_COOLDOWN_MILLIS = 2000L;

    private final StrataHub plugin;
    private final Map<UUID, Long> lastNoticeAt = new HashMap<>();

    BarrierNoClip(StrataHub plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!enabled()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.distanceSquared(to) < MOVE_CHECK_DISTANCE * MOVE_CHECK_DISTANCE) {
            return;
        }
        if (findBarrierHit(from, to) != null) {
            // a real wall just stops you where you were - walking or gliding, no partial credit
            event.setTo(from);
            notify(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        checkTeleport(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        checkTeleport(event);
    }

    private void checkTeleport(PlayerTeleportEvent event) {
        if (!enabled() || event.getTo() == null) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return; // a barrier can't span two worlds - nothing on this line to trace
        }
        RayTraceResult hit = findBarrierHit(from, to);
        if (hit == null) {
            return;
        }
        Vector direction = to.toVector().subtract(from.toVector());
        if (direction.lengthSquared() < 1.0e-6) {
            event.setCancelled(true);
        } else {
            Vector landing = hit.getHitPosition().subtract(direction.normalize().multiply(LANDING_PULLBACK));
            event.setTo(new Location(from.getWorld(), landing.getX(), landing.getY(), landing.getZ(), to.getYaw(), to.getPitch()));
        }
        notify(event.getPlayer());
    }

    private boolean enabled() {
        return this.plugin.getConfig().getBoolean("barrier-noclip", true);
    }

    // a bundle of parallel rays covering the player's actual hitbox (roughly 0.6 wide, ~1.8 tall) instead of
    // a single centre-to-centre line, so a fast, shallow-angle clip can't graze past a wall between two rays
    private static final double[][] HITBOX_OFFSETS = {
        {0, 0.05, 0}, {0.28, 0.05, 0}, {-0.28, 0.05, 0}, {0, 0.05, 0.28}, {0, 0.05, -0.28},
        {0, 0.9, 0}, {0, 1.7, 0}
    };

    private RayTraceResult findBarrierHit(Location from, Location to) {
        World world = from.getWorld();
        Vector delta = to.toVector().subtract(from.toVector());
        double distance = delta.length();
        if (distance < 1.0e-4) {
            return null;
        }
        Vector direction = delta.normalize();
        for (double[] offset : HITBOX_OFFSETS) {
            Location start = from.clone().add(offset[0], offset[1], offset[2]);
            RayTraceResult hit = world.rayTraceBlocks(start, direction, distance, FluidCollisionMode.NEVER, true);
            if (hit != null && hit.getHitBlock() != null && hit.getHitBlock().getType() == Material.BARRIER) {
                return hit;
            }
        }
        return null;
    }

    private void notify(Player player) {
        long now = System.currentTimeMillis();
        Long last = this.lastNoticeAt.get(player.getUniqueId());
        if (last != null && now - last < NOTICE_COOLDOWN_MILLIS) {
            return;
        }
        this.lastNoticeAt.put(player.getUniqueId(), now);
        player.sendActionBar(Component.text("That's a barrier - nothing gets through it.", NamedTextColor.RED));
    }
}
