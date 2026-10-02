package com.stratasmp.stratammo.quests;

import com.stratasmp.stratammo.Skill;
import java.util.Set;

public record QuestDef(String id, String name, ObjectiveType type, Set<String> targets, int amount, Skill skill, int xp) {
   public boolean matches(ObjectiveType objective, String key) {
      if (objective != this.type) return false;
      if (this.targets.isEmpty()) return true;
      String normalized = key.toUpperCase(java.util.Locale.ROOT);
      if (objective == ObjectiveType.BREW_POTION) {
         normalized = normalized.replaceFirst("^(LONG_|STRONG_)", "");
      }
      return this.targets.contains(normalized);
   }

   public String describe() {
      String what = this.targets.isEmpty() ? "" : " (" + String.join("/", this.targets).toLowerCase().replace('_', ' ') + ")";
      return this.name + what;
   }
}
