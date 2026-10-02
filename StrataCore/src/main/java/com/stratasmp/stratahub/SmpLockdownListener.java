package com.stratasmp.stratahub;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;

/** While /stratahub lockdown is on, keeps non-bypassing players out of the SMP worlds entirely - teleports and portals alike. */
public final class SmpLockdownListener implements Listener {

    private final StrataHub plugin;

    public SmpLockdownListener(StrataHub plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (isBlocked(event.getPlayer(), event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (isBlocked(event.getPlayer(), event)) {
            event.setCancelled(true);
        }
    }

    private boolean isBlocked(Player player, PlayerTeleportEvent event) {
        if (!plugin.hubData().isSmpLockdown() || hasBypass(player)) {
            return false;
        }
        if (event.getTo() == null || event.getTo().getWorld() == null) {
            return false;
        }
        String toWorld = event.getTo().getWorld().getName();
        List<String> lockedWorlds = plugin.getConfig().getStringList("lockdown.worlds");
        if (!lockedWorlds.contains(toWorld)) {
            return false;
        }
        plugin.msg().send(player, "lockdown-blocked");
        return true;
    }

    private boolean hasBypass(Player player) {
        if (player.hasPermission("stratahub.lockdown.bypass")) {
            return true;
        }
        for (String name : plugin.getConfig().getStringList("lockdown.bypass-players")) {
            if (name.equalsIgnoreCase(player.getName())) {
                return true;
            }
        }
        return false;
    }
}
