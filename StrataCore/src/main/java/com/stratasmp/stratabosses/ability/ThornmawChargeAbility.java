package com.stratasmp.stratabosses.ability;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class ThornmawChargeAbility implements BossAbility {
   private static final double RANGE = 15.0;
   private static final double DAMAGE = 14.0;
   private static final int TELEGRAPH_TICKS = 15;
   private static final int CHARGE_TICKS = 16;
   private static final double HIT_RADIUS = 2.2;

   @Override
   public void execute(StrataModule plugin, final LivingEntity boss) {
      Player target = this.nearestPlayer(boss, 15.0);
      if (target != null) {
         final Vector direction = target.getLocation().toVector().subtract(boss.getLocation().toVector());
         direction.setY(0);
         if (!(direction.lengthSquared() < 0.01)) {
            direction.normalize();
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.5F, 0.8F);
            (new BukkitRunnable() {
                  int ticksElapsed = 0;
                  final Set<Player> alreadyHit = new HashSet<>();

                  public void run() {
                     if (boss.isValid() && !boss.isDead()) {
                        this.ticksElapsed++;
                        if (this.ticksElapsed <= 15) {
                           Location tell = boss.getLocation().add(direction.clone().multiply(2));
                           boss.getWorld().spawnParticle(Particle.CRIT, tell, 6, 0.3, 0.3, 0.3);
                        } else if (this.ticksElapsed > 31) {
                           this.cancel();
                        } else {
                           boss.setVelocity(direction.clone().multiply(1.1).setY(0.05));

                           for (Player player : boss.getWorld().getPlayers()) {
                              if ((player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                                 && !this.alreadyHit.contains(player)
                                 && !(player.getLocation().distance(boss.getLocation()) > 2.2)) {
                                 this.alreadyHit.add(player);
                                 com.stratasmp.stratabosses.BossAbilities.damage(player, 14.0, boss);
                                 Vector knock = player.getLocation().toVector().subtract(boss.getLocation().toVector());
                                 if (knock.lengthSquared() < 0.01) {
                                    knock = direction.clone();
                                 }

                                 knock.normalize().setY(0.5);
                                 player.setVelocity(knock.multiply(1.8));
                              }
                           }
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
