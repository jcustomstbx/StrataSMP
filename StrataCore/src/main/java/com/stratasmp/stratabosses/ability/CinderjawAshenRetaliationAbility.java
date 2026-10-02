package com.stratasmp.stratabosses.ability;

import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import com.stratasmp.stratacore.StrataModule;

public class CinderjawAshenRetaliationAbility implements BossAbility {
   private static final double RADIUS = 3.5;
   private static final double DAMAGE = 7.0;
   private static final int FIRE_TICKS = 60;

   @Override
   public void execute(StrataModule plugin, LivingEntity boss) {
      boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_BLAZE_BURN, 1.5F, 1.0F);
      boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation().add(0.0, 1.0, 0.0), 40, 1.5, 0.8, 1.5, 0.05);
      boss.getWorld().spawnParticle(Particle.LAVA, boss.getLocation(), 10, 1.2, 0.3, 1.2);

      for (Player player : boss.getWorld().getPlayers()) {
         if ((player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
            && !(player.getLocation().distance(boss.getLocation()) > 3.5)) {
            com.stratasmp.stratabosses.BossAbilities.damage(player, 7.0, boss);
            player.setFireTicks(Math.max(player.getFireTicks(), 60));
         }
      }
   }
}
