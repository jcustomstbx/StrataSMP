package com.stratasmp.stratabosses.ability;

import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class AbyssalRiptideWaveAbility implements BossAbility {
   private static final double RADIUS = 6.0;
   private static final double DAMAGE = 5.0;
   private static final int SLOW_TICKS = 50;

   @Override
   public void execute(StrataModule plugin, LivingEntity boss) {
      boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_DROWNED_SWIM, 1.6F, 0.6F);
      boss.getWorld().spawnParticle(Particle.SPLASH, boss.getLocation().add(0.0, 1.0, 0.0), 60, 3.0, 0.5, 3.0);

      for (Player player : boss.getWorld().getPlayers()) {
         if ((player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
            && !(player.getLocation().distance(boss.getLocation()) > 6.0)) {
            com.stratasmp.stratabosses.BossAbilities.damage(player, 5.0, boss);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 1));
            Vector push = player.getLocation().toVector().subtract(boss.getLocation().toVector());
            if (push.lengthSquared() < 0.01) {
               push = new Vector(1, 0, 0);
            }

            push.normalize().setY(0.3);
            player.setVelocity(push.multiply(1.8));
         }
      }
   }
}
