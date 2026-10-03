package com.stratasmp.stratabosses;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.EntityType;

public class BossDefinition {
   public final String id;
   public final String displayName;
   public final EntityType baseType;
   public final boolean wearsArmor;
   public final Color armorColor;
   public final Material weaponMaterial;
   public final int weaponModelData;
   public final String weaponDisplayName;
   public final BarColor bossBarColor;
   public String modelId;
   public String weaponModelId;
   public boolean enabled = true;
   public double maxHealth = 1000.0;
   public double attackDamage = 16.0;
   public double knockbackResistance = 0.85;
   public double knockbackTakenMultiplier = 0.2;
   public double movementSpeed = 0.32;
   public double followRange = 48.0;
   public double spawnChance = 0.015;
   public int maxConcurrentPerWorld = 1;
   public long respawnCooldownSeconds = 1800L;
   public long abilityCooldownSeconds = 9L;
   public double stratasReward = 15000.0;
   public final java.util.List<LootRoll> bonusLoot = new java.util.ArrayList<>();

   public BossDefinition(
      String id,
      String displayName,
      EntityType baseType,
      boolean wearsArmor,
      Color armorColor,
      Material weaponMaterial,
      int weaponModelData,
      String weaponDisplayName,
      BarColor bossBarColor
   ) {
      this.id = id;
      this.modelId = id;
      this.weaponModelId = id.equals("thornmaw") ? "" : id + "_weapon";
      this.displayName = displayName;
      this.baseType = baseType;
      this.wearsArmor = wearsArmor;
      this.armorColor = armorColor;
      this.weaponMaterial = weaponMaterial;
      this.weaponModelData = weaponModelData;
      this.weaponDisplayName = weaponDisplayName;
      this.bossBarColor = bossBarColor;
   }
}
