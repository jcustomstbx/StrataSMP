package com.stratasmp.stratateams;

import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class FriendlyFireListener implements Listener {
   private final TeamManager teams;
   private final CombatTracker combat;

   public FriendlyFireListener(TeamManager teams, CombatTracker combat) {
      this.teams = teams;
      this.combat = combat;
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onDamage(EntityDamageByEntityEvent event) {
      UUID attackerUuid = this.resolveAttacker(event.getDamager());
      if (attackerUuid != null) {
         Entity victim = event.getEntity();
         UUID victimPlayerUuid = victim instanceof Player victimPlayer ? victimPlayer.getUniqueId() : null;
         UUID damageVictimUuid = victimPlayerUuid;
         if (victimPlayerUuid == null && victim instanceof Tameable tameable && tameable.isTamed() && tameable.getOwner() != null) {
            damageVictimUuid = tameable.getOwner().getUniqueId();
         }

         if (damageVictimUuid != null && !attackerUuid.equals(damageVictimUuid)
               && this.teams.sameTeamAndFriendlyFireOff(attackerUuid, damageVictimUuid)) {
            event.setCancelled(true);
         }
      }
   }

   /**
    * Tags both sides only after every other plugin has had its say: a cancelled hit, a zero-damage snowball or a
    * punch on a Citizens NPC (which is a Player entity) must not block /thome.
    */
   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onDamageTag(EntityDamageByEntityEvent event) {
      UUID attackerUuid = this.resolveAttacker(event.getDamager());
      if (attackerUuid == null || !(event.getEntity() instanceof Player victim)) {
         return;
      }
      if (event.getFinalDamage() <= 0.0 || victim.hasMetadata("NPC") || victim.hasMetadata("strataduels-active")) {
         return;
      }
      if (attackerUuid.equals(victim.getUniqueId())) {
         return;
      }
      this.combat.markInCombat(attackerUuid);
      this.combat.markInCombat(victim.getUniqueId());
   }

   @EventHandler
   public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
      this.combat.clearCombat(event.getEntity().getUniqueId());
   }

   @EventHandler
   public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
      this.combat.clearCombat(event.getPlayer().getUniqueId());
   }

   private UUID resolveAttacker(Entity damager) {
      if (damager instanceof Player player) {
         return player.getUniqueId();
      } else {
         return damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter ? shooter.getUniqueId() : null;
      }
   }
}
