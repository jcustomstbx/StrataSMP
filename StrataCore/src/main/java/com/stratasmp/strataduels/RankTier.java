package com.stratasmp.strataduels;

import org.bukkit.configuration.ConfigurationSection;

public enum RankTier {
   BRONZE("Bronze"),
   SILVER("Silver"),
   GOLD("Gold"),
   PLATINUM("Platinum"),
   DIAMOND("Diamond"),
   CHAMPION("Champion");

   private final String displayName;

   private RankTier(String displayName) {
      this.displayName = displayName;
   }

   public String displayName() {
      return this.displayName;
   }

   public static RankTier forElo(double elo, ConfigurationSection thresholds) {
      RankTier tier = BRONZE;

      for (RankTier candidate : values()) {
         int threshold = thresholds.getInt(candidate.name().toLowerCase(), 0);
         if (elo >= threshold) {
            tier = candidate;
         }
      }

      return tier;
   }
}
