package com.stratasmp.stratabosses.ability;

import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.util.Vector;

public class AbyssalPullAbility implements BossAbility {
   private static final double RANGE = 14.0;
   private static final double DAMAGE = 6.0;

   @Override
   public void execute(StrataModule plugin, LivingEntity boss) {
      Player target = this.nearestPlayer(boss, 14.0);
      if (target != null) {
         boss.getWorld().playSound(boss.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.4F, 0.7F);
         boss.getWorld().spawnParticle(Particle.BUBBLE_POP, target.getLocation().add(0.0, 1.0, 0.0), 20, 0.4, 0.6, 0.4);
         Vector pull = boss.getLocation().toVector().subtract(target.getLocation().toVector());
         if (!(pull.lengthSquared() < 0.01)) {
            pull.normalize().multiply(1.3).setY(0.25);
            target.setVelocity(pull);
            com.stratasmp.stratabosses.BossAbilities.damage(target, 6.0, boss);
         }
      }
   }

   private Player nearestPlayer(LivingEntity boss, double range) {
      Player nearest = null;
      double best = range * range;

      for (Player player : boss.getWorld().getPlayers()) {
         if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
            double distSq = player.getLocation().distanceSquared(boss.getLocation());
            if (distSq < best) {
               best = distSq;
               nearest = player;
            }
         }
      }

      return nearest;
   }
}
