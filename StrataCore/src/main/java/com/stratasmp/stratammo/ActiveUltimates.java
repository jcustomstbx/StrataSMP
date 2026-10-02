package com.stratasmp.stratammo;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveUltimates {
   private final Map<UUID, Map<Skill, Long>> active = new ConcurrentHashMap<>();

   public void activate(UUID player, Skill skill, int durationSeconds) {
      this.active.computeIfAbsent(player, k -> new EnumMap<>(Skill.class)).put(skill, System.currentTimeMillis() + durationSeconds * 1000L);
   }

   public boolean isActive(UUID player, Skill skill) {
      Map<Skill, Long> perSkill = this.active.get(player);
      if (perSkill == null) {
         return false;
      } else {
         Long expiry = perSkill.get(skill);
         return expiry != null && expiry > System.currentTimeMillis();
      }
   }
}
