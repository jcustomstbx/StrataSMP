package com.stratasmp.stratabosses.ability;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.persistence.PersistentDataType;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class VaelspireVolleyAbility implements BossAbility {
   private static final double RANGE = 20.0;
   private static final int SHOTS = 3;
   public static final String PROJECTILE_MARKER = "boss-projectile";

   @Override
   public void execute(StrataModule plugin, final LivingEntity boss) {
      final Player target = this.nearestPlayer(boss, 20.0);
      if (target != null) {
         final NamespacedKey marker = new NamespacedKey(plugin, "boss-projectile");
         (new BukkitRunnable() {
            int shot = 0;

            public void run() {
               if (!boss.isValid() || boss.isDead() || this.shot >= 3) {
                  this.cancel();
               } else if (target.isValid() && !target.isDead()) {
                  Location eye = boss.getEyeLocation();
                  Vector direction = target.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
                  WitherSkull skull = (WitherSkull)boss.getWorld().spawn(eye, WitherSkull.class);
                  skull.setShooter(boss);
                  skull.setDirection(direction);
                  skull.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte)1);
                  this.shot++;
               } else {
                  this.cancel();
               }
            }
         }).runTaskTimer(plugin, 0L, 6L);
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
