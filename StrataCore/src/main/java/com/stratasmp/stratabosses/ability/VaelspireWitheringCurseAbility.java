package com.stratasmp.stratabosses.ability;

import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class VaelspireWitheringCurseAbility implements BossAbility {
   private static final double RANGE = 18.0;
   private static final int DURATION_TICKS = 120;

   @Override
   public void execute(StrataModule plugin, LivingEntity boss) {
      Player target = this.nearestPlayer(boss, 18.0);
      if (target != null) {
         boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.5F, 0.6F);
         boss.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0.0, 1.0, 0.0), 25, 0.4, 0.6, 0.4, 0.02);
         target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 120, 1));
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
