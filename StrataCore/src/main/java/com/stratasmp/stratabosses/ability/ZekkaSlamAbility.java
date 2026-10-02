package com.stratasmp.stratabosses.ability;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.util.Vector;

public class ZekkaSlamAbility implements BossAbility {
   private static final double RADIUS = 5.0;
   private static final double DAMAGE = 12.0;

   @Override
   public void execute(StrataModule plugin, LivingEntity boss) {
      Location center = boss.getLocation();
      center.getWorld().spawnParticle(Particle.EXPLOSION, center.clone().add(0.0, 0.2, 0.0), 1);
      center.getWorld().playSound(center, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.6F, 0.7F);

      for (Player player : center.getWorld().getPlayers()) {
         if ((player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) && !(player.getLocation().distance(center) > 5.0)) {
            com.stratasmp.stratabosses.BossAbilities.damage(player, 12.0, boss);
            Vector push = player.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 0.01) {
               push = new Vector(1, 0, 0);
            }

            push.normalize().setY(0.4);
            player.setVelocity(push.multiply(1.6));
         }
      }
   }
}
