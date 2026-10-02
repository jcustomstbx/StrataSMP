package com.stratasmp.stratahub;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

/** Players are invincible in the hub, so without this anyone who fell off the island would fall forever. */
final class VoidRescueListener implements Listener {

    private final StrataHub plugin;

    VoidRescueListener(StrataHub plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || to.getY() >= plugin.getConfig().getDouble("void-rescue-below-y", 60.0)) {
            return;
        }
        World world = to.getWorld();
        if (!world.getName().equalsIgnoreCase(plugin.getConfig().getString("hub-world", "hub"))) {
            return;
        }
        Player player = event.getPlayer();
        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));
        player.teleport(world.getSpawnLocation());
    }
}
