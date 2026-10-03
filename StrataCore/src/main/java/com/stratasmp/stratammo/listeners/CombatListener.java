package com.stratasmp.stratammo.listeners;

import com.stratasmp.stratammo.Skill;
import com.stratasmp.stratammo.XpNotifier;
import com.stratasmp.stratammo.XpValues;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

public class CombatListener implements Listener {
   private final XpValues xpValues;
   private final XpNotifier notifier;
   private final Map<UUID, Boolean> lastHitWasProjectile = new HashMap<>();

   public CombatListener(XpValues xpValues, XpNotifier notifier) {
      this.xpValues = xpValues;
      this.notifier = notifier;
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onDamage(EntityDamageByEntityEvent event) {
      if (event.getEntity() instanceof Player player && !this.notifier.accepts(player)) return;
      if (event.getDamager() instanceof Player player && !this.notifier.accepts(player)) return;
      if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player
            && !this.notifier.accepts(player)) return;
      boolean ranged = event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player;
      // only hits by players matter; recording mob-vs-mob damage leaked an entry per entity
      if (!ranged && !(event.getDamager() instanceof Player)) return;
      if (this.lastHitWasProjectile.size() > 5000) this.lastHitWasProjectile.clear();
      this.lastHitWasProjectile.put(event.getEntity().getUniqueId(), ranged);
   }

   @EventHandler
   public void onDeath(EntityDeathEvent event) {
      Player killer = event.getEntity().getKiller();
      // keystone run mobs pay nothing, so a run can't be started and abandoned for free xp
      if (com.stratasmp.stratakeystones.KeystoneMobs.isRunMob(event.getEntity())) {
         this.lastHitWasProjectile.remove(event.getEntity().getUniqueId());
         return;
      }
      if (killer != null && !this.notifier.accepts(killer)) {
         this.lastHitWasProjectile.remove(event.getEntity().getUniqueId());
         return;
      }
      Boolean wasProjectile = this.lastHitWasProjectile.remove(event.getEntity().getUniqueId());
      if (killer != null) {
         if (event.getEntity() instanceof Monster || event.getEntity() instanceof Boss) {
            int xp = this.xpValues.combat(event.getEntityType());
            this.notifier.award(killer, this.resolveSkill(killer, wasProjectile), xp);
         }
      }
   }

   private Skill resolveSkill(Player killer, Boolean wasProjectile) {
      if (Boolean.TRUE.equals(wasProjectile)) {
         return Skill.ARCHERY;
      } else {
         ItemStack weapon = killer.getInventory().getItemInMainHand();
         Material type = weapon.getType();
         String name = type.name();
         if (name.endsWith("_SWORD")) {
            return Skill.SWORDS;
         } else {
            return name.endsWith("_AXE") ? Skill.AXES : Skill.UNARMED;
         }
      }
   }
}
