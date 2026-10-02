package com.stratasmp.stratabosses.ability;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.metadata.FixedMetadataValue;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.util.Vector;

public class CinderjawMagmaBoltAbility implements BossAbility {
   private static final double RANGE = 20.0;
   public static final String PROJECTILE_MARKER = "stratabosses:cinderjaw_magma_bolt";

   @Override
   public void execute(StrataModule plugin, LivingEntity boss) {
      Player target = this.nearestPlayer(boss, 20.0);
      if (target != null) {
         boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.4F, 0.8F);
         Location eye = boss.getEyeLocation();
         Vector direction = target.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
         SmallFireball bolt = (SmallFireball)boss.getWorld().spawn(eye, SmallFireball.class, fb -> {
            fb.setShooter(boss);
            fb.setDirection(direction);
            fb.setIsIncendiary(false);
            fb.setYield(0.0F);
            fb.setMetadata("stratabosses:cinderjaw_magma_bolt", new FixedMetadataValue(plugin, true));
         });
         bolt.setVelocity(direction.multiply(1.6));
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
