package com.stratasmp.stratammo;

import org.bukkit.configuration.ConfigurationSection;

public class PerkSettings {
   private boolean enabled = true;
   private long minorBuffCooldownSeconds = 60L;
   private long utilityCooldownSeconds = 300L;
   private long conservationCooldownSeconds = 1800L;
   private long ultimateCooldownSeconds = 1800L;
   private int ultimateDurationSeconds = 20;
   private int miningHasteProcChance = 10;
   private int miningBlastFurnaceChance = 30;
   private int miningResonanceChance = 8;
   private int miningSeismicShiftMaxBlocks = 12;
   private int woodcuttingLumberjackPaceChance = 10;
   private int woodcuttingResinExtractionChance = 15;
   private int woodcuttingRootSystemChance = 10;
   private int woodcuttingTimberFallMaxLogs = 48;
   private int farmingGreenThumbChance = 15;
   private int farmingFertileSoilChance = 10;
   private int farmingBountifulHarvestChance = 10;
   private int farmingBountifulHarvestXpBonusPercent = 50;
   private int farmingNaturesWrathRadius = 1;
   private int fishingReflexesWaitReductionPercent = 15;
   private int fishingTreasureHunterBonusPercent = 20;
   private int fishingCatchAndReleaseChance = 15;
   private int enchantingAetherialFocusBonusPercent = 10;
   private int enchantingResonanceInfusionChance = 10;
   private int repairSteadyHandsCostReductionPercent = 20;
   private int repairReinforcedBuildBonusPercent = 10;
   private int repairReinforcedBuildMaxStacks = 5;
   private int repairSalvageArtistChance = 25;
   private int alchemyPracticedBrewerChance = 15;
   private int alchemyPotentMixtureChance = 15;
   private int alchemyWasteNotChance = 20;

   public PerkSettings(ConfigurationSection perks) {
      if (perks != null) {
         this.enabled = perks.getBoolean("enabled", this.enabled);
         this.minorBuffCooldownSeconds = perks.getLong("minor-buff-cooldown-seconds", this.minorBuffCooldownSeconds);
         this.utilityCooldownSeconds = perks.getLong("utility-cooldown-seconds", this.utilityCooldownSeconds);
         this.conservationCooldownSeconds = perks.getLong("conservation-cooldown-seconds", this.conservationCooldownSeconds);
         this.ultimateCooldownSeconds = perks.getLong("ultimate-cooldown-seconds", this.ultimateCooldownSeconds);
         this.ultimateDurationSeconds = perks.getInt("ultimate-duration-seconds", this.ultimateDurationSeconds);
         ConfigurationSection mining = perks.getConfigurationSection("mining");
         if (mining != null) {
            this.miningHasteProcChance = mining.getInt("haste-proc-chance", this.miningHasteProcChance);
            this.miningBlastFurnaceChance = mining.getInt("blast-furnace-chance", this.miningBlastFurnaceChance);
            this.miningResonanceChance = mining.getInt("resonance-chance", this.miningResonanceChance);
            this.miningSeismicShiftMaxBlocks = mining.getInt("seismic-shift-max-blocks", this.miningSeismicShiftMaxBlocks);
         }

         ConfigurationSection woodcutting = perks.getConfigurationSection("woodcutting");
         if (woodcutting != null) {
            this.woodcuttingLumberjackPaceChance = woodcutting.getInt("lumberjack-pace-chance", this.woodcuttingLumberjackPaceChance);
            this.woodcuttingResinExtractionChance = woodcutting.getInt("resin-extraction-chance", this.woodcuttingResinExtractionChance);
            this.woodcuttingRootSystemChance = woodcutting.getInt("root-system-chance", this.woodcuttingRootSystemChance);
            this.woodcuttingTimberFallMaxLogs = woodcutting.getInt("timber-fall-max-logs", this.woodcuttingTimberFallMaxLogs);
         }

         ConfigurationSection farming = perks.getConfigurationSection("farming");
         if (farming != null) {
            this.farmingGreenThumbChance = farming.getInt("green-thumb-chance", this.farmingGreenThumbChance);
            this.farmingFertileSoilChance = farming.getInt("fertile-soil-chance", this.farmingFertileSoilChance);
            this.farmingBountifulHarvestChance = farming.getInt("bountiful-harvest-chance", this.farmingBountifulHarvestChance);
            this.farmingBountifulHarvestXpBonusPercent = farming.getInt("bountiful-harvest-xp-bonus-percent", this.farmingBountifulHarvestXpBonusPercent);
            this.farmingNaturesWrathRadius = farming.getInt("natures-wrath-radius", this.farmingNaturesWrathRadius);
         }

         ConfigurationSection fishing = perks.getConfigurationSection("fishing");
         if (fishing != null) {
            this.fishingReflexesWaitReductionPercent = fishing.getInt("reflexes-wait-reduction-percent", this.fishingReflexesWaitReductionPercent);
            this.fishingTreasureHunterBonusPercent = fishing.getInt("treasure-hunter-bonus-percent", this.fishingTreasureHunterBonusPercent);
            this.fishingCatchAndReleaseChance = fishing.getInt("catch-and-release-chance", this.fishingCatchAndReleaseChance);
         }

         ConfigurationSection enchanting = perks.getConfigurationSection("enchanting");
         if (enchanting != null) {
            this.enchantingAetherialFocusBonusPercent = enchanting.getInt("aetherial-focus-bonus-percent", this.enchantingAetherialFocusBonusPercent);
            this.enchantingResonanceInfusionChance = enchanting.getInt("resonance-infusion-chance", this.enchantingResonanceInfusionChance);
         }

         ConfigurationSection repair = perks.getConfigurationSection("repair");
         if (repair != null) {
            this.repairSteadyHandsCostReductionPercent = repair.getInt("steady-hands-cost-reduction-percent", this.repairSteadyHandsCostReductionPercent);
            this.repairReinforcedBuildBonusPercent = repair.getInt("reinforced-build-bonus-percent", this.repairReinforcedBuildBonusPercent);
            this.repairReinforcedBuildMaxStacks = repair.getInt("reinforced-build-max-stacks", this.repairReinforcedBuildMaxStacks);
            this.repairSalvageArtistChance = repair.getInt("salvage-artist-chance", this.repairSalvageArtistChance);
         }

         ConfigurationSection alchemy = perks.getConfigurationSection("alchemy");
         if (alchemy != null) {
            this.alchemyPracticedBrewerChance = alchemy.getInt("practiced-brewer-chance", this.alchemyPracticedBrewerChance);
            this.alchemyPotentMixtureChance = alchemy.getInt("potent-mixture-chance", this.alchemyPotentMixtureChance);
            this.alchemyWasteNotChance = alchemy.getInt("waste-not-chance", this.alchemyWasteNotChance);
         }
      }
   }

   public boolean enabled() {
      return this.enabled;
   }

   public long minorBuffCooldownSeconds() {
      return this.minorBuffCooldownSeconds;
   }

   public long utilityCooldownSeconds() {
      return this.utilityCooldownSeconds;
   }

   public long conservationCooldownSeconds() {
      return this.conservationCooldownSeconds;
   }

   public long ultimateCooldownSeconds() {
      return this.ultimateCooldownSeconds;
   }

   public int ultimateDurationSeconds() {
      return this.ultimateDurationSeconds;
   }

   public int miningHasteProcChance() {
      return this.miningHasteProcChance;
   }

   public int miningBlastFurnaceChance() {
      return this.miningBlastFurnaceChance;
   }

   public int miningResonanceChance() {
      return this.miningResonanceChance;
   }

   public int miningSeismicShiftMaxBlocks() {
      return this.miningSeismicShiftMaxBlocks;
   }

   public int woodcuttingLumberjackPaceChance() {
      return this.woodcuttingLumberjackPaceChance;
   }

   public int woodcuttingResinExtractionChance() {
      return this.woodcuttingResinExtractionChance;
   }

   public int woodcuttingRootSystemChance() {
      return this.woodcuttingRootSystemChance;
   }

   public int woodcuttingTimberFallMaxLogs() {
      return this.woodcuttingTimberFallMaxLogs;
   }

   public int farmingGreenThumbChance() {
      return this.farmingGreenThumbChance;
   }

   public int farmingFertileSoilChance() {
      return this.farmingFertileSoilChance;
   }

   public int farmingBountifulHarvestChance() {
      return this.farmingBountifulHarvestChance;
   }

   public int farmingBountifulHarvestXpBonusPercent() {
      return this.farmingBountifulHarvestXpBonusPercent;
   }

   public int farmingNaturesWrathRadius() {
      return this.farmingNaturesWrathRadius;
   }

   public int fishingReflexesWaitReductionPercent() {
      return this.fishingReflexesWaitReductionPercent;
   }

   public int fishingTreasureHunterBonusPercent() {
      return this.fishingTreasureHunterBonusPercent;
   }

   public int fishingCatchAndReleaseChance() {
      return this.fishingCatchAndReleaseChance;
   }

   public int enchantingAetherialFocusBonusPercent() {
      return this.enchantingAetherialFocusBonusPercent;
   }

   public int enchantingResonanceInfusionChance() {
      return this.enchantingResonanceInfusionChance;
   }

   public int repairSteadyHandsCostReductionPercent() {
      return this.repairSteadyHandsCostReductionPercent;
   }

   public int repairReinforcedBuildBonusPercent() {
      return this.repairReinforcedBuildBonusPercent;
   }

   public int repairReinforcedBuildMaxStacks() {
      return this.repairReinforcedBuildMaxStacks;
   }

   public int repairSalvageArtistChance() {
      return this.repairSalvageArtistChance;
   }

   public int alchemyPracticedBrewerChance() {
      return this.alchemyPracticedBrewerChance;
   }

   public int alchemyPotentMixtureChance() {
      return this.alchemyPotentMixtureChance;
   }

   public int alchemyWasteNotChance() {
      return this.alchemyWasteNotChance;
   }
}
