package com.stratasmp.stratabosses.ability;

import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.util.Vector;

public class VaelspireBoneBarrierAbility implements BossAbility {
   private static final double RADIUS = 4.0;
   private static final double DAMAGE = 6.0;

   @Override
   public void execute(StrataModule plugin, LivingEntity boss) {
      boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_BONE_BLOCK_BREAK, 1.4F, 0.7F);
      boss.getWorld().spawnParticle(Particle.CRIT, boss.getLocation().add(0.0, 1.0, 0.0), 20, 1.2, 0.6, 1.2);

      for (Player player : boss.getWorld().getPlayers()) {
         if ((player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
            && !(player.getLocation().distance(boss.getLocation()) > 4.0)) {
            com.stratasmp.stratabosses.BossAbilities.damage(player, 6.0, boss);
            Vector push = player.getLocation().toVector().subtract(boss.getLocation().toVector());
            if (push.lengthSquared() < 0.01) {
               push = new Vector(1, 0, 0);
            }

            push.normalize().setY(0.35);
            player.setVelocity(push.multiply(2.0));
         }
      }
   }
}
