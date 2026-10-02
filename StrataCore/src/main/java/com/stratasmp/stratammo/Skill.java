package com.stratasmp.stratammo;

public enum Skill {
   MINING("Mining"),
   WOODCUTTING("Woodcutting"),
   FARMING("Farming"),
   SWORDS("Swords"),
   AXES("Axes"),
   ARCHERY("Archery"),
   UNARMED("Unarmed"),
   FISHING("Fishing"),
   ENCHANTING("Enchanting"),
   REPAIR("Repair"),
   ALCHEMY("Alchemy");

   private final String displayName;

   private Skill(String displayName) {
      this.displayName = displayName;
   }

   public String displayName() {
      return this.displayName;
   }
}
