package com.stratasmp.stratateams;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import com.stratasmp.stratacore.StrataModule;

public class TeleportFlightGrace implements Listener {
   private final StrataModule plugin;
   private final long graceTicks;

   public TeleportFlightGrace(StrataModule plugin) {
      this.plugin = plugin;
      this.graceTicks = plugin.getConfig().getLong("teleport-flight-grace-seconds", 5L) * 20L;
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onTeleport(PlayerTeleportEvent event) {
      TeleportCause cause = event.getCause();
      if (cause == TeleportCause.COMMAND || cause == TeleportCause.PLUGIN) {
         Player player = event.getPlayer();
         if (!player.getAllowFlight()) {
            player.setAllowFlight(true);
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
               if (player.isOnline()) {
                  player.setAllowFlight(false);
               }
            }, this.graceTicks);
         }
      }
   }
}
