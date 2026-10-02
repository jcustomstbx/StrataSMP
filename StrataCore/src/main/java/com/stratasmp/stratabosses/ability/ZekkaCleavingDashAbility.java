package com.stratasmp.stratabosses.ability;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class ZekkaCleavingDashAbility implements BossAbility {
   private static final double RANGE = 10.0;
   private static final double DAMAGE = 10.0;
   private static final int TELEGRAPH_TICKS = 4;
   private static final int DASH_TICKS = 7;
   private static final double HIT_RADIUS = 2.0;

   @Override
   public void execute(StrataModule plugin, final LivingEntity boss) {
      Player target = this.nearestPlayer(boss, 10.0);
      if (target != null) {
         final Vector direction = target.getLocation().toVector().subtract(boss.getLocation().toVector());
         direction.setY(0);
         if (!(direction.lengthSquared() < 0.01)) {
            direction.normalize();
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_PIGLIN_BRUTE_HURT, 1.3F, 0.6F);
            (new BukkitRunnable() {
                  int ticksElapsed = 0;
                  boolean hit = false;

                  public void run() {
                     if (boss.isValid() && !boss.isDead()) {
                        this.ticksElapsed++;
                        if (this.ticksElapsed <= 4) {
                           boss.getWorld().spawnParticle(Particle.CRIT, boss.getLocation().add(0.0, 1.0, 0.0), 4, 0.2, 0.2, 0.2);
                        } else if (!this.hit && this.ticksElapsed <= 11) {
                           boss.setVelocity(direction.clone().multiply(1.3).setY(0.05));
                           Location loc = boss.getLocation();

                           for (Player player : boss.getWorld().getPlayers()) {
                              if ((player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                                 && !(player.getLocation().distance(loc) > 2.0)) {
                                 this.hit = true;
                                 com.stratasmp.stratabosses.BossAbilities.damage(player, 10.0, boss);
                                 Vector knock = player.getLocation().toVector().subtract(loc.toVector());
                                 if (knock.lengthSquared() < 0.01) {
                                    knock = direction.clone();
                                 }

                                 knock.normalize().setY(0.45);
                                 player.setVelocity(knock.multiply(1.6));
                                 break;
                              }
                           }
                        } else {
                           this.cancel();
                        }
                     } else {
                        this.cancel();
                     }
                  }
               })
               .runTaskTimer(plugin, 0L, 1L);
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
