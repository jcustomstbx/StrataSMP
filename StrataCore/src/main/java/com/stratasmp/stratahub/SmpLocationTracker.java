package com.stratasmp.stratahub;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Keeps each player's "last position in the SMP" fresh - on any teleport out of it, and on logging out inside it. */
public final class SmpLocationTracker implements Listener {

    private final StrataHub plugin;

    public SmpLocationTracker(StrataHub plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        String smpWorld = plugin.getConfig().getString("smp-world", "world");
        Location from = event.getFrom();
        Location to = event.getTo();
        boolean leavingSmp = from.getWorld() != null && from.getWorld().getName().equals(smpWorld)
            && (to == null || to.getWorld() == null || !to.getWorld().getName().equals(smpWorld));
        if (leavingSmp) {
            plugin.playerLocations().set(event.getPlayer().getUniqueId(), from);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String smpWorld = plugin.getConfig().getString("smp-world", "world");
        if (player.getWorld().getName().equals(smpWorld)) {
            plugin.playerLocations().set(player.getUniqueId(), player.getLocation());
        }
    }
}
