package com.stratasmp.stratahub;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * A marker for ops: a beacon an op places keeps a coloured beam of light shooting straight up to the build
 * limit for as long as the beacon is there, so a spot can be pointed out from far away. The beam is coloured
 * dust redrawn every half second for players nearby, and it is remembered in beacons.yml so it survives a
 * restart. Breaking the beacon (or anything else removing it) ends the beam. The beacon itself stays an
 * ordinary block.
 */
final class OpBeaconBeam extends BukkitRunnable implements Listener {

    private static final String DEFAULT_COLOUR = "#33C8FF";
    private static final double SPACING = 1.5;
    private static final double VIEW_DISTANCE = 96.0;
    private static final double WINDOW_BELOW = 40.0;
    private static final double WINDOW_ABOVE = 80.0;

    private record Spot(String world, int x, int y, int z) {
        String encode() {
            return world + ";" + x + ";" + y + ";" + z;
        }

        static Spot decode(String text) {
            String[] p = text.split(";");
            return new Spot(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        }
    }

    private final StrataHub plugin;
    private final File file;
    private final Set<Spot> spots = new LinkedHashSet<>();

    OpBeaconBeam(StrataHub plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "beacons.yml");
        load();
    }

    void start() {
        runTaskTimer(plugin, 40L, 10L);
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        for (String text : YamlConfiguration.loadConfiguration(file).getStringList("beacons")) {
            try {
                spots.add(Spot.decode(text));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Skipping bad beacon entry '" + text + "' in beacons.yml");
            }
        }
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        List<String> lines = new ArrayList<>();
        for (Spot spot : spots) {
            lines.add(spot.encode());
        }
        cfg.set("beacons", lines);
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't save beacons.yml: " + e.getMessage());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.BEACON
                || !event.getPlayer().isOp()
                || !plugin.getConfig().getBoolean("op-beacon-beam", true)) {
            return;
        }
        var block = event.getBlockPlaced();
        if (spots.add(new Spot(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()))) {
            save();
        }
        var centre = block.getLocation().add(0.5, 1.0, 0.5);
        block.getWorld().playSound(centre, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 1.0f);
        block.getWorld().playSound(centre, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2.0f, 0.7f);
    }

    @Override
    public void run() {
        if (spots.isEmpty() || !plugin.getConfig().getBoolean("op-beacon-beam", true)) {
            return;
        }
        String setting = plugin.getConfig().getString("op-beacon-beam-color", DEFAULT_COLOUR).trim();
        boolean rainbow = setting.equalsIgnoreCase("rainbow");
        Color fixed = rainbow ? null : parse(setting);

        boolean changed = false;
        for (var it = spots.iterator(); it.hasNext(); ) {
            Spot spot = it.next();
            World world = Bukkit.getWorld(spot.world());
            if (world == null || !world.isChunkLoaded(spot.x() >> 4, spot.z() >> 4)) {
                continue;
            }
            if (world.getBlockAt(spot.x(), spot.y(), spot.z()).getType() != Material.BEACON) {
                it.remove();
                changed = true;
                continue;
            }
            draw(world, spot, rainbow, fixed);
        }
        if (changed) {
            save();
        }
    }

    private void draw(World world, Spot spot, boolean rainbow, Color fixed) {
        double cx = spot.x() + 0.5;
        double cz = spot.z() + 0.5;
        double bottom = spot.y() + 1.0;
        double top = world.getMaxHeight();
        for (Player player : world.getPlayers()) {
            if (Math.hypot(player.getX() - cx, player.getZ() - cz) > VIEW_DISTANCE) {
                continue;
            }
            // only the part of the column around the viewer, so a tall beam stays cheap
            double from = Math.max(bottom, player.getY() - WINDOW_BELOW);
            double to = Math.min(top, player.getY() + WINDOW_ABOVE);
            from = bottom + Math.floor((from - bottom) / SPACING) * SPACING;
            for (double y = from; y < to; y += SPACING) {
                Color colour = rainbow ? hue(((y - bottom) / 24.0) % 1.0) : fixed;
                player.spawnParticle(Particle.DUST, cx, y, cz, 2, 0.06, 0.0, 0.06, 0.0,
                        new Particle.DustOptions(colour, 2.2f), true);
            }
        }
    }

    private static Color parse(String hex) {
        try {
            return Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
        } catch (IllegalArgumentException e) {
            return Color.fromRGB(Integer.parseInt(DEFAULT_COLOUR.substring(1), 16));
        }
    }

    /** Fully saturated colour for a hue between 0 and 1, so the rainbow needs no java.desktop. */
    private static Color hue(double h) {
        double x = 1.0 - Math.abs((h * 6.0) % 2.0 - 1.0);
        double[][] sectors = {{1, x, 0}, {x, 1, 0}, {0, 1, x}, {0, x, 1}, {x, 0, 1}, {1, 0, x}};
        double[] c = sectors[Math.min(5, (int) (h * 6.0))];
        return Color.fromRGB((int) (c[0] * 255), (int) (c[1] * 255), (int) (c[2] * 255));
    }
}
