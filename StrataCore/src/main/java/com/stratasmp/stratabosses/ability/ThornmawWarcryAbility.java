package com.stratasmp.stratabosses.ability;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.LivingEntity;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class ThornmawWarcryAbility implements BossAbility {
   private static final int DURATION_TICKS = 100;
   private static final double BONUS_KNOCKBACK_RESISTANCE = 0.5;
   private static final NamespacedKey MODIFIER_KEY = new NamespacedKey("stratabosses", "warcry_knockback_resist");

   @Override
   public void execute(StrataModule plugin, LivingEntity boss) {
      boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.6F, 0.8F);
      boss.getWorld().spawnParticle(Particle.SWEEP_ATTACK, boss.getLocation().add(0.0, 1.0, 0.0), 6, 0.6, 0.4, 0.6);
      boss.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1, false, true));
      AttributeInstance knockbackResist = boss.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
      if (knockbackResist != null) {
         knockbackResist.removeModifier(MODIFIER_KEY);
         knockbackResist.addModifier(new AttributeModifier(MODIFIER_KEY, 0.5, Operation.ADD_NUMBER));
         plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (boss.isValid() && !boss.isDead()) {
               knockbackResist.removeModifier(MODIFIER_KEY);
            }
         }, 100L);
      }
   }
}
