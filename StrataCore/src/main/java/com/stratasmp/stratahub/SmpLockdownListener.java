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

    /** Someone who logged out inside a locked world must not simply log back in during a lockdown. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        sendToHubIfLocked(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (event.getRespawnLocation().getWorld() != null && isLocked(player, event.getRespawnLocation().getWorld().getName())) {
            org.bukkit.World hub = plugin.getServer().getWorld(plugin.getConfig().getString("hub-world", "hub"));
            if (hub != null) event.setRespawnLocation(hub.getSpawnLocation());
        }
    }

    private void sendToHubIfLocked(Player player) {
        if (!isLocked(player, player.getWorld().getName())) return;
        org.bukkit.World hub = plugin.getServer().getWorld(plugin.getConfig().getString("hub-world", "hub"));
        if (hub != null) {
            player.teleport(hub.getSpawnLocation());
            plugin.msg().send(player, "lockdown-blocked");
        }
    }

    private boolean isLocked(Player player, String worldName) {
        return plugin.hubData().isSmpLockdown() && !hasBypass(player)
                && plugin.getConfig().getStringList("lockdown.worlds").contains(worldName);
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
