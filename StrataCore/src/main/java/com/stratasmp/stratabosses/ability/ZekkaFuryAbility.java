package com.stratasmp.stratabosses.ability;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class ZekkaFuryAbility implements BossAbility {
   private static final int DURATION_TICKS = 100;

   @Override
   public void execute(StrataModule plugin, LivingEntity boss) {
      boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 1.6F, 0.7F);
      boss.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, boss.getLocation().add(0.0, 1.5, 0.0), 12, 0.5, 0.5, 0.5);
      boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, false, true));
      boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0, false, true));
   }
}
