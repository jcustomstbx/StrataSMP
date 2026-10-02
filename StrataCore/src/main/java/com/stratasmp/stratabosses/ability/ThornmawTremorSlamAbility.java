package com.stratasmp.stratabosses.ability;

import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class ThornmawTremorSlamAbility implements BossAbility {
   private static final double RADIUS = 5.5;
   private static final double DAMAGE = 9.0;
   private static final int SLOW_TICKS = 60;

   @Override
   public void execute(StrataModule plugin, LivingEntity boss) {
      boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ATTACK, 1.5F, 0.6F);
      boss.getWorld().spawnParticle(Particle.EXPLOSION, boss.getLocation(), 3, 1.0, 0.2, 1.0);
      boss.getWorld()
         .spawnParticle(Particle.BLOCK_CRUMBLE, boss.getLocation(), 40, 2.5, 0.2, 2.5, boss.getLocation().getBlock().getRelative(0, -1, 0).getBlockData());

      for (Player player : boss.getWorld().getPlayers()) {
         if ((player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
            && !(player.getLocation().distance(boss.getLocation()) > 5.5)) {
            com.stratasmp.stratabosses.BossAbilities.damage(player, 9.0, boss);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
         }
      }
   }
}
