package com.stratasmp.stratammo;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class PlayerData {
   public final UUID uuid;
   public String lastKnownName;
   private final Map<Skill, Integer> xp = new EnumMap<>(Skill.class);

   public PlayerData(UUID uuid, String lastKnownName) {
      this.uuid = uuid;
      this.lastKnownName = lastKnownName;

      for (Skill skill : Skill.values()) {
         this.xp.put(skill, 0);
      }
   }

   public int getXp(Skill skill) {
      return this.xp.getOrDefault(skill, 0);
   }

   public void setXp(Skill skill, int amount) {
      this.xp.put(skill, Math.max(0, amount));
   }

   public void addXp(Skill skill, int amount) {
      this.setXp(skill, this.getXp(skill) + amount);
   }

   public int totalXpAcrossSkills() {
      int sum = 0;

      for (int v : this.xp.values()) {
         sum += v;
      }

      return sum;
   }
}
