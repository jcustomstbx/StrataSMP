package com.stratasmp.stratateams;

import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
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

         if (damageVictimUuid != null && !attackerUuid.equals(damageVictimUuid)) {
            if (this.teams.sameTeamAndFriendlyFireOff(attackerUuid, damageVictimUuid)) {
               event.setCancelled(true);
            } else {
               if (victimPlayerUuid != null && !(victim instanceof Player victimPlayerx && victimPlayerx.hasMetadata("strataduels-active"))) {
                  this.combat.markInCombat(attackerUuid);
                  this.combat.markInCombat(victimPlayerUuid);
               }
            }
         }
      }
   }

   private UUID resolveAttacker(Entity damager) {
      if (damager instanceof Player player) {
         return player.getUniqueId();
      } else {
         return damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter ? shooter.getUniqueId() : null;
      }
   }
}
