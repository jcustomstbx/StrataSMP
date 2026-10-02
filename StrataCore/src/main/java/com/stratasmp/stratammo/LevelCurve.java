package com.stratasmp.stratammo;

public class LevelCurve {
   private final int base;
   private final int perLevel;
   private final int maxLevel;

   public LevelCurve(int base, int perLevel, int maxLevel) {
      this.base = base;
      this.perLevel = perLevel;
      this.maxLevel = maxLevel;
   }

   public int xpForLevel(int level) {
      return this.base + level * this.perLevel;
   }

   public int maxLevel() {
      return this.maxLevel;
   }

   public boolean isCapped() {
      return this.maxLevel > 0;
   }

   public int[] levelFromTotalXp(int totalXp) {
      int level = 0;

      int remaining;
      for (remaining = totalXp; (!this.isCapped() || level < this.maxLevel) && remaining >= this.xpForLevel(level); level++) {
         remaining -= this.xpForLevel(level);
      }

      return this.isCapped() && level >= this.maxLevel ? new int[]{this.maxLevel, 0} : new int[]{level, remaining};
   }
}
