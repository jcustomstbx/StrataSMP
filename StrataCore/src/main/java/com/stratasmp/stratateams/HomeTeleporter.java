package com.stratasmp.stratateams;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.scheduler.BukkitTask;

public class HomeTeleporter implements Listener {
   private final StrataModule plugin;
   private final TeamManager teams;
   private final CombatTracker combat;
   private final long warmupTicks;
   private final Map<UUID, HomeTeleporter.Pending> pending = new HashMap<>();

   public HomeTeleporter(StrataModule plugin, TeamManager teams, CombatTracker combat) {
      this.plugin = plugin;
      this.teams = teams;
      this.combat = combat;
      this.warmupTicks = plugin.getConfig().getLong("thome-warmup-seconds", 5L) * 20L;
   }

   public void startTeleport(Player player) {
      Team team = this.teams.getTeam(player.getUniqueId());
      if (team == null) {
         player.sendMessage("You're not in a team.");
      } else if (team.home == null) {
         player.sendMessage("Your team hasn't set a home yet.");
      } else if (this.combat.isInCombat(player.getUniqueId())) {
         player.sendMessage(this.msg("&cYou can't teleport while in combat! (" + this.combat.secondsRemaining(player.getUniqueId()) + "s left)"));
      } else if (this.pending.containsKey(player.getUniqueId())) {
         player.sendMessage("You're already teleporting.");
      } else {
         long seconds = this.warmupTicks / 20L;
         player.sendMessage(this.msg("&eTeleporting to the team home in " + seconds + "s - don't move or take damage!"));
         BukkitTask task = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            this.pending.remove(player.getUniqueId());
            if (player.isOnline()) {
               Team currentTeam = this.teams.getTeam(player.getUniqueId());
               if (currentTeam != null && currentTeam.home != null) {
                  if (this.combat.isInCombat(player.getUniqueId())) {
                     player.sendMessage(this.msg("&cTeleport cancelled - you're in combat."));
                  } else {
                     player.teleport(currentTeam.home);
                     player.sendMessage(this.msg("&aTeleported to the team home."));
                  }
               } else {
                  player.sendMessage("Your team home is no longer available.");
               }
            }
         }, this.warmupTicks);
         this.pending.put(player.getUniqueId(), new HomeTeleporter.Pending(task));
      }
   }

   private void cancel(Player player, String reason) {
      HomeTeleporter.Pending p = this.pending.remove(player.getUniqueId());
      if (p != null) {
         p.task().cancel();
         player.sendMessage(this.msg("&cTeleport cancelled - " + reason + "."));
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onMove(PlayerMoveEvent event) {
      if (this.pending.containsKey(event.getPlayer().getUniqueId())) {
         if (event.getFrom().getBlockX() != event.getTo().getBlockX()
            || event.getFrom().getBlockY() != event.getTo().getBlockY()
            || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            this.cancel(event.getPlayer(), "you moved");
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onDamage(EntityDamageEvent event) {
      if (event.getEntity() instanceof Player player) {
         if (this.pending.containsKey(player.getUniqueId())) {
            this.cancel(player, "you took damage");
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      HomeTeleporter.Pending p = this.pending.remove(event.getPlayer().getUniqueId());
      if (p != null) {
         p.task().cancel();
      }

      Player player = event.getPlayer();
      if (!player.isDead() && !(player.getHealth() <= 0.0)) {
         if (this.combat.isInCombat(player.getUniqueId())) {
            player.setHealth(0.0);
            this.plugin.getServer().broadcast(this.msg("&c" + player.getName() + " combat logged and was slain."));
         }
      }
   }

   private Component msg(String legacy) {
      return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy);
   }

   private record Pending(BukkitTask task) {
   }
}
