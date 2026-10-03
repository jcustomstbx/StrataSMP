package com.stratasmp.stratahub;

import com.stratasmp.strataranks.RankTier;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

/** Ornate and Regal get the same RankEssentials RTP destination with a 75-tick warmup. */
final class RankRtpWarmup implements Listener {
    private final StrataHub plugin;
    private boolean broken;
    private Method getManager;
    private Method findSafe;
    private Field cooldownsField;

    RankRtpWarmup(StrataHub plugin) { this.plugin = plugin; }

    /** Same RankEssentials combat tag the safe-zone guard reads; if it can't be reached the check is skipped. */
    private boolean inCombat(Plugin rank, UUID id) {
        try {
            Object manager = rank.getClass().getMethod("getCombatManager").invoke(rank);
            if (manager == null) return false;
            return (boolean) manager.getClass().getMethod("isInCombat", UUID.class).invoke(manager, id);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String raw = event.getMessage().toLowerCase(java.util.Locale.ROOT).trim();
        if ((!raw.equals("/rtp") && !raw.equals("/rankessentials:rtp"))
                || !player.getWorld().getName().equals(plugin.getConfig().getString("smp-world", "world"))
                || RankTier.of(player).ordinal() < RankTier.ORNATE.ordinal()
                || broken) return;
        Plugin rank = plugin.getServer().getPluginManager().getPlugin("RankEssentials");
        if (rank == null || !rank.isEnabled()) return;
        try {
            if (getManager == null) getManager = rank.getClass().getMethod("getTPManager");
            Object manager = getManager.invoke(rank);
            if (cooldownsField == null) {
                cooldownsField = manager.getClass().getField("rtpCooldowns");
                findSafe = manager.getClass().getMethod("findSafeRTPLocation", World.class);
            }
            @SuppressWarnings("unchecked")
            Map<UUID, Long> cooldowns = (Map<UUID, Long>) cooldownsField.get(manager);
            UUID id = player.getUniqueId();
            long now = System.currentTimeMillis();
            long remaining = cooldowns.getOrDefault(id, 0L) - now;
            event.setCancelled(true);
            if (inCombat(rank, id)) {
                player.sendMessage("You can't use /rtp while in combat.");
                return;
            }
            if (remaining > 0) {
                player.sendMessage("/rtp is ready in " + Math.max(1, (remaining + 999) / 1000) + " seconds.");
                return;
            }
            cooldowns.put(id, now + plugin.getConfig().getLong("ornate-rtp-cooldown-seconds", 90L) * 1000L);
            Location start = player.getLocation().clone();
            player.sendMessage("Teleporting in 3.75 seconds. Stay still.");
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || !player.getWorld().equals(start.getWorld()) || player.isGliding()
                        || player.getLocation().distanceSquared(start) > 0.05) {
                    cooldowns.remove(id);
                    if (player.isOnline()) player.sendMessage("Teleport cancelled because you moved; no cooldown was charged.");
                    return;
                }
                // a hit during the warmup tags the player, and the check at /rtp time no longer applies
                if (inCombat(rank, id)) {
                    cooldowns.remove(id);
                    player.sendMessage("Teleport cancelled because you are in combat; no cooldown was charged.");
                    return;
                }
                try {
                    Location target = (Location) findSafe.invoke(manager, player.getWorld());
                    if (target == null) { cooldowns.remove(id); player.sendMessage("No safe RTP location was found."); return; }
                    if (player.teleport(target)) player.sendMessage("Teleported to a random safe location.");
                    else { cooldowns.remove(id); player.sendMessage("RTP was blocked; no cooldown was charged."); }
                } catch (ReflectiveOperationException | RuntimeException error) {
                    cooldowns.remove(id);
                    plugin.getLogger().warning("Rank RTP destination lookup failed: " + error);
                    player.sendMessage("RTP failed; no cooldown was charged.");
                }
            }, 75L);
        } catch (ReflectiveOperationException | RuntimeException error) {
            broken = true;
            plugin.getLogger().warning("Rank RTP integration disabled; RankEssentials will handle /rtp: " + error);
        }
    }
}
