package com.stratasmp.stratammo;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

public class XpValues {
   private final Map<Material, Integer> mining = new HashMap<>();
   private final Map<Material, Integer> woodcutting = new HashMap<>();
   private final Map<Material, Integer> farming = new HashMap<>();
   private final Map<EntityType, Integer> combat = new HashMap<>();
   private int combatDefault = 8;
   private int fishingCatch = 8;
   private int fishingTreasure = 20;
   private int enchantingPerLevelCost = 3;
   private int repairPerLevelCost = 4;
   private int alchemyBase = 12;
   private int alchemySplashBonus = 6;
   private int alchemyLingeringBonus = 10;

   public XpValues(ConfigurationSection xpSection) {
      if (xpSection != null) {
         this.loadMaterials(xpSection.getConfigurationSection("mining"), this.mining);
         this.loadMaterials(xpSection.getConfigurationSection("woodcutting"), this.woodcutting);
         this.loadMaterials(xpSection.getConfigurationSection("farming"), this.farming);
         ConfigurationSection combatSection = xpSection.getConfigurationSection("combat");
         if (combatSection != null) {
            for (String key : combatSection.getKeys(false)) {
               if (key.equalsIgnoreCase("default")) {
                  this.combatDefault = combatSection.getInt(key);
               } else {
                  try {
                     EntityType type = EntityType.valueOf(key.toUpperCase());
                     this.combat.put(type, combatSection.getInt(key));
                  } catch (IllegalArgumentException var7) {
                  }
               }
            }
         }

         ConfigurationSection fishingSection = xpSection.getConfigurationSection("fishing");
         if (fishingSection != null) {
            this.fishingCatch = fishingSection.getInt("catch", this.fishingCatch);
            this.fishingTreasure = fishingSection.getInt("treasure", this.fishingTreasure);
         }

         ConfigurationSection enchantingSection = xpSection.getConfigurationSection("enchanting");
         if (enchantingSection != null) {
            this.enchantingPerLevelCost = enchantingSection.getInt("per-level-cost", this.enchantingPerLevelCost);
         }

         ConfigurationSection repairSection = xpSection.getConfigurationSection("repair");
         if (repairSection != null) {
            this.repairPerLevelCost = repairSection.getInt("per-level-cost", this.repairPerLevelCost);
         }

         ConfigurationSection alchemySection = xpSection.getConfigurationSection("alchemy");
         if (alchemySection != null) {
            this.alchemyBase = alchemySection.getInt("base", this.alchemyBase);
            this.alchemySplashBonus = alchemySection.getInt("splash-bonus", this.alchemySplashBonus);
            this.alchemyLingeringBonus = alchemySection.getInt("lingering-bonus", this.alchemyLingeringBonus);
         }
      }
   }

   private void loadMaterials(ConfigurationSection section, Map<Material, Integer> target) {
      if (section != null) {
         for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material != null) {
               target.put(material, section.getInt(key));
            }
         }
      }
   }

   public int mining(Material material) {
      return this.mining.getOrDefault(material, 0);
   }

   public int woodcutting(Material material) {
      return this.woodcutting.getOrDefault(material, 0);
   }

   public int farming(Material material) {
      return this.farming.getOrDefault(material, 0);
   }

   public int combat(EntityType type) {
      return this.combat.getOrDefault(type, this.combatDefault);
   }

   public int fishingCatch() {
      return this.fishingCatch;
   }

   public int fishingTreasure() {
      return this.fishingTreasure;
   }

   public int enchanting(int levelCost) {
      return levelCost * this.enchantingPerLevelCost;
   }

   public int repair(int levelCost) {
      return levelCost * this.repairPerLevelCost;
   }

   public int alchemy(Material potionType) {
      int total = this.alchemyBase;
      if (potionType == Material.SPLASH_POTION) {
         total += this.alchemySplashBonus;
      } else if (potionType == Material.LINGERING_POTION) {
         total += this.alchemyLingeringBonus;
      }

      return total;
   }
}
