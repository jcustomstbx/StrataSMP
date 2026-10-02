package com.stratasmp.stratabosses.ability;

import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class AbyssalMaelstromAbility implements BossAbility {
   private static final double RANGE = 12.0;
   private static final double GRAB_RADIUS = 2.5;
   private static final double FINISH_DAMAGE = 9.0;
   private static final int DURATION_TICKS = 60;

   @Override
   public void execute(StrataModule plugin, final LivingEntity boss) {
      boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_DROWNED_AMBIENT_WATER, 1.8F, 0.5F);
      (new BukkitRunnable() {
         int ticksElapsed = 0;

         public void run() {
            if (boss.isValid() && !boss.isDead() && this.ticksElapsed < 60) {
               this.ticksElapsed++;
               boss.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, boss.getLocation().add(0.0, 0.5, 0.0), 8, 6.0, 0.3, 6.0);

               for (Player player : boss.getWorld().getPlayers()) {
                  if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                     double dist = player.getLocation().distance(boss.getLocation());
                     if (!(dist > 12.0)) {
                        if (dist <= 2.5) {
                           com.stratasmp.stratabosses.BossAbilities.damage(player, 9.0, boss);
                        } else {
                           Vector pull = boss.getLocation().toVector().subtract(player.getLocation().toVector());
                           pull.normalize().multiply(0.35).setY(0.05);
                           player.setVelocity(player.getVelocity().add(pull));
                        }
                     }
                  }
               }
            } else {
               this.cancel();
            }
         }
      }).runTaskTimer(plugin, 0L, 1L);
   }
}
