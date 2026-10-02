package com.stratasmp.stratahub;

import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Draws a red outline of each configured safe zone (the WorldGuard region does the protecting; this
 * only shows where its edge is) so anyone stepping out of it can see they have left the safe area.
 * The outline is particles at the player's own height, sent only to players in that world who are near
 * the zone, so it follows the ground and costs nothing when nobody is close.
 */
final class SafeZoneOutline extends BukkitRunnable {

    private static final double VIEW_DISTANCE = 40.0;
    private static final int LAYERS = 3;
    private static final double SPACING = 0.5;
    private static final double CORNER_POST = 5.0;

    private final StrataHub plugin;

    SafeZoneOutline(StrataHub plugin) {
        this.plugin = plugin;
    }

    void start() {
        runTaskTimer(plugin, 40L, 10L);
    }

    @Override
    public void run() {
        List<Map<?, ?>> zones = plugin.getConfig().getMapList("safe-zone-outlines");
        for (Map<?, ?> zone : zones) {
            try {
                draw(zone);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Bad safe-zone-outlines entry " + zone + ": " + e);
            }
        }
    }

    private void draw(Map<?, ?> zone) {
        World world = Bukkit.getWorld(String.valueOf(zone.get("world")));
        if (world == null) {
            return;
        }
        int minX = number(zone, "min-x");
        int maxX = number(zone, "max-x");
        int minZ = number(zone, "min-z");
        int maxZ = number(zone, "max-z");
        Object hex = zone.get("color");
        Color colour = Color.fromRGB(Integer.parseInt(hex == null ? "FF2020" : String.valueOf(hex).replace("#", ""), 16));
        Particle.DustOptions dust = new Particle.DustOptions(colour, 1.6f);

        // the region covers whole blocks, so its outer edge sits one past the last block
        double x0 = Math.min(minX, maxX);
        double x1 = Math.max(minX, maxX) + 1;
        double z0 = Math.min(minZ, maxZ);
        double z1 = Math.max(minZ, maxZ) + 1;

        for (Player player : world.getPlayers()) {
            double dx = Math.max(Math.max(x0 - player.getX(), player.getX() - x1), 0);
            double dz = Math.max(Math.max(z0 - player.getZ(), player.getZ() - z1), 0);
            if (Math.hypot(dx, dz) > VIEW_DISTANCE) {
                continue;
            }
            double base = Math.floor(player.getY());
            for (int layer = 0; layer < LAYERS; layer++) {
                double y = base + layer + 0.1;
                // a dot every half block, so the edge reads as a line however many dots have faded
                for (double x = x0; x <= x1; x += SPACING) {
                    player.spawnParticle(Particle.DUST, x, y, z0, 1, 0, 0, 0, 0, dust);
                    player.spawnParticle(Particle.DUST, x, y, z1, 1, 0, 0, 0, 0, dust);
                }
                for (double z = z0 + SPACING; z < z1; z += SPACING) {
                    player.spawnParticle(Particle.DUST, x0, y, z, 1, 0, 0, 0, 0, dust);
                    player.spawnParticle(Particle.DUST, x1, y, z, 1, 0, 0, 0, 0, dust);
                }
            }
            // taller posts at the four corners so the exact extent is easy to count against
            for (double y = base - 1.0; y <= base + CORNER_POST; y += SPACING) {
                player.spawnParticle(Particle.DUST, x0, y, z0, 1, 0, 0, 0, 0, dust);
                player.spawnParticle(Particle.DUST, x1, y, z0, 1, 0, 0, 0, 0, dust);
                player.spawnParticle(Particle.DUST, x0, y, z1, 1, 0, 0, 0, 0, dust);
                player.spawnParticle(Particle.DUST, x1, y, z1, 1, 0, 0, 0, 0, dust);
            }
        }
    }

    private static int number(Map<?, ?> zone, String key) {
        Object value = zone.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("missing number '" + key + "'");
        }
        return number.intValue();
    }
}
