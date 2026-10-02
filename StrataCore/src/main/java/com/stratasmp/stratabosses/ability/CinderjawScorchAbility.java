package com.stratasmp.stratabosses.ability;

import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class CinderjawScorchAbility implements BossAbility {
   private static final double RADIUS = 4.5;
   private static final int FIRE_TICKS = 100;

   @Override
   public void execute(StrataModule plugin, LivingEntity boss) {
      boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation().add(0.0, 1.0, 0.0), 40, 1.5, 1.0, 1.5, 0.02);
      boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 1.5F, 1.0F);

      for (Player player : boss.getWorld().getPlayers()) {
         if ((player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
            && !(player.getLocation().distance(boss.getLocation()) > 4.5)) {
            player.setFireTicks(Math.max(player.getFireTicks(), 100));
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 1));
         }
      }
   }
}
