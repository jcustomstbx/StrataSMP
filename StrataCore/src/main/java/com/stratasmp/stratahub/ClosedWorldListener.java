package com.stratasmp.stratahub;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Keeps everyone without stratahub.closedworld.bypass out of the worlds listed under closed-worlds - portals and
 * teleports alike. It lives in the config, so unlike RankEssentials' /endlock it survives a restart; to open a world,
 * take it out of the list and run /stratahub reload.
 */
final class ClosedWorldListener implements Listener {

    private final StrataHub plugin;

    ClosedWorldListener(StrataHub plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (isClosed(event.getPlayer(), event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (isClosed(event.getPlayer(), event.getTo())) {
            event.setCancelled(true);
        }
    }

    private boolean isClosed(Player player, Location to) {
        if (to == null || to.getWorld() == null) {
            return false;
        }
        if (!plugin.getConfig().getStringList("closed-worlds").contains(to.getWorld().getName())) {
            return false;
        }
        if (player.hasPermission("stratahub.closedworld.bypass")) {
            return false;
        }
        plugin.msg().send(player, "closed-world");
        return true;
    }
}
