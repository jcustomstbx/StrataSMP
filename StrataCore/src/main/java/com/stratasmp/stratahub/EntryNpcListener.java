package com.stratasmp.stratahub;

import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;

/**
 * Right-clicking a configured entry NPC random-teleports the player into the SMP. With entry-always-rtp turned off,
 * returning players are instead sent back to where they left off (first-timers are always random-teleported).
 */
public final class EntryNpcListener implements Listener {

    private final StrataHub plugin;

    public EntryNpcListener(StrataHub plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(NPCRightClickEvent event) {
        if (plugin.hubData().isComingSoonNpc(event.getNPC().getId())) {
            plugin.msg().send(event.getClicker(), "factions-coming-soon");
            return;
        }
        if (!plugin.hubData().isEntryNpc(event.getNPC().getId())) {
            return;
        }
        Player player = event.getClicker();
        UUID uuid = player.getUniqueId();
        boolean alwaysRtp = plugin.getConfig().getBoolean("entry-always-rtp", true);
        Location saved = alwaysRtp ? null : plugin.playerLocations().get(uuid);
        if (saved != null) {
            // a lockdown or closed world cancels the teleport; say nothing more and stay put
            if (player.teleport(saved)) plugin.msg().send(player, "returning");
            return;
        }

        World smpWorld = plugin.getServer().getWorld(plugin.getConfig().getString("smp-world", "world"));
        if (smpWorld == null) {
            player.sendMessage("The SMP world isn't loaded - tell an admin.");
            return;
        }
        // a cancelled teleport (lockdown, closed world) must not fall through to /rtp from inside the hub
        if (!player.teleport(smpWorld.getSpawnLocation())) return;
        plugin.msg().send(player, alwaysRtp ? "entry" : "first-entry");
        clearRtpCooldown(uuid);
        player.performCommand("rtp");
    }

    /**
     * RankEssentials gives /rtp a 5 minute cooldown. Every click on the NPC is meant to land somewhere new, so a
     * player coming back within 5 minutes would otherwise be left standing at the SMP spawn with a "cooldown" message.
     * Only the NPC clears it - typing /rtp yourself still has the cooldown.
     */
    private void clearRtpCooldown(UUID uuid) {
        try {
            Plugin rankEssentials = plugin.getServer().getPluginManager().getPlugin("RankEssentials");
            if (rankEssentials == null) {
                return;
            }
            Object tpManager = rankEssentials.getClass().getMethod("getTPManager").invoke(rankEssentials);
            Object cooldowns = tpManager.getClass().getField("rtpCooldowns").get(tpManager);
            ((Map<?, ?>) cooldowns).remove(uuid);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Couldn't clear the RankEssentials /rtp cooldown for an NPC entry: " + e);
        }
    }
}
