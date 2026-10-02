package com.stratasmp.stratabosses;

import com.stratasmp.stratabosses.ability.AbyssalMaelstromAbility;
import com.stratasmp.stratabosses.ability.AbyssalTridentThrowAbility;
import com.stratasmp.stratabosses.ability.AbyssalPullAbility;
import com.stratasmp.stratabosses.ability.AbyssalRiptideWaveAbility;
import com.stratasmp.stratabosses.ability.BossAbility;
import com.stratasmp.stratabosses.ability.CinderjawAshenRetaliationAbility;
import com.stratasmp.stratabosses.ability.CinderjawMagmaBoltAbility;
import com.stratasmp.stratabosses.ability.CinderjawScorchAbility;
import com.stratasmp.stratabosses.ability.ThornmawChargeAbility;
import com.stratasmp.stratabosses.ability.ThornmawTremorSlamAbility;
import com.stratasmp.stratabosses.ability.ThornmawWarcryAbility;
import com.stratasmp.stratabosses.ability.VaelspireBoneBarrierAbility;
import com.stratasmp.stratabosses.ability.VaelspireVolleyAbility;
import com.stratasmp.stratabosses.ability.VaelspireWitheringCurseAbility;
import com.stratasmp.stratabosses.ability.ZekkaCleavingDashAbility;
import com.stratasmp.stratabosses.ability.ZekkaFuryAbility;
import com.stratasmp.stratabosses.ability.ZekkaSlamAbility;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.LivingEntity;
import com.stratasmp.stratacore.StrataModule;

public final class BossAbilities {
   private static final Map<String, List<BossAbility>> ROTATION = Map.of(
      "zekka",
      List.of(new ZekkaSlamAbility(), new ZekkaCleavingDashAbility()),
      "vaelspire",
      List.of(new VaelspireVolleyAbility(), new VaelspireWitheringCurseAbility()),
      "thornmaw",
      List.of(new ThornmawChargeAbility(), new ThornmawTremorSlamAbility()),
      "abyssal_coilfang",
      List.of(new AbyssalTridentThrowAbility(), new AbyssalMaelstromAbility(), new AbyssalPullAbility()),
      "cinderjaw",
      List.of(new CinderjawScorchAbility(), new CinderjawMagmaBoltAbility())
   );
   private static final Map<String, BossAbility> REACTIVE = Map.of(
      "zekka",
      new ZekkaFuryAbility(),
      "vaelspire",
      new VaelspireBoneBarrierAbility(),
      "thornmaw",
      new ThornmawWarcryAbility(),
      "abyssal_coilfang",
      new AbyssalRiptideWaveAbility(),
      "cinderjaw",
      new CinderjawAshenRetaliationAbility()
   );
   private static final Map<UUID, BossAbility> lastUsed = new ConcurrentHashMap<>();

   // Ability damage must not be interpreted as a native weapon swing. This also
   // wraps delayed pulse/dash damage, not just the initial ability invocation.
   private static final java.util.Set<UUID> abilityDamage = new java.util.HashSet<>();

   public static boolean isAbilityDamage(UUID bossId) { return abilityDamage.contains(bossId); }

   // scales every ability hit and the Coilfang trident; set from config (ability-damage-multiplier)
   private static volatile double damageMultiplier = 1.0;

   public static void setDamageMultiplier(double multiplier) { damageMultiplier = Math.max(0.0, multiplier); }

   public static double damageMultiplier() { return damageMultiplier; }

   public static void damage(LivingEntity target, double amount, LivingEntity boss) {
      boolean added = abilityDamage.add(boss.getUniqueId());
      try { target.damage(amount * damageMultiplier, boss); }
      finally { if (added) abilityDamage.remove(boss.getUniqueId()); }
   }

   private BossAbilities() {
   }

   public static void executeRotation(StrataModule plugin, String bossId, LivingEntity boss) {
      List<BossAbility> pool = ROTATION.get(bossId);
      if (pool != null && !pool.isEmpty()) {
         BossAbility choice;
         if (pool.size() == 1) {
            choice = pool.get(0);
         } else {
            BossAbility previous = lastUsed.get(boss.getUniqueId());

            do {
               choice = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            } while (choice == previous);
         }

         lastUsed.put(boss.getUniqueId(), choice);
         choice.execute(plugin, boss);
      }
   }

   public static void executeReactive(StrataModule plugin, String bossId, LivingEntity boss) {
      BossAbility ability = REACTIVE.get(bossId);
      if (ability != null) {
         ability.execute(plugin, boss);
      }
   }

   public static void clearState(UUID bossUuid) {
      lastUsed.remove(bossUuid);
   }
}
