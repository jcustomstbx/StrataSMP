package com.stratasmp.stratabosses;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import com.stratasmp.stratacore.StrataModule;

public class BossRegistry {
   private final Map<String, BossDefinition> byId = new LinkedHashMap<>();

   public BossRegistry(StrataModule plugin) {
      this.register(
         new BossDefinition(
            "zekka", "Zekka the Brute", EntityType.PIGLIN_BRUTE, true, Color.fromRGB(122, 42, 42), Material.GOLDEN_AXE, 1001, "Zekka Cleaver", BarColor.RED
         )
      );
      this.register(
         new BossDefinition(
            "vaelspire",
            "Vaelspire the Hollow",
            EntityType.WITHER_SKELETON,
            true,
            Color.fromRGB(201, 194, 173),
            Material.IRON_SWORD,
            1002,
            "Vaelspire Edge",
            BarColor.WHITE
         )
      );
      this.register(
         new BossDefinition(
            "thornmaw", "Thornmaw", EntityType.RAVAGER, false, Color.fromRGB(91, 74, 47), Material.STONE_AXE, 1003, "Thornmaw Club", BarColor.GREEN
         )
      );
      this.register(
         new BossDefinition(
            "abyssal_coilfang", "Abyssal Coilfang", EntityType.DROWNED, true, Color.fromRGB(18, 58, 61), Material.TRIDENT, 1004, "Abyssal Lance", BarColor.BLUE
         )
      );
      this.register(
         new BossDefinition(
            "cinderjaw", "Cinderjaw", EntityType.HUSK, true, Color.fromRGB(42, 26, 20), Material.IRON_AXE, 1005, "Cinderjaw Axe", BarColor.YELLOW
         )
      );
      this.reload(plugin);
   }

   private void register(BossDefinition def) {
      this.byId.put(def.id, def);
   }

   public void reload(StrataModule plugin) {
      ConfigurationSection bosses = plugin.getConfig().getConfigurationSection("bosses");
      if (bosses != null) {
         for (BossDefinition def : this.byId.values()) {
            ConfigurationSection section = bosses.getConfigurationSection(def.id);
            if (section != null) {
               def.modelId = section.getString("model-id", def.id);
               def.weaponModelId = section.getString("weapon-model-id", def.weaponModelId);
               def.enabled = section.getBoolean("enabled", def.enabled);
               def.maxHealth = section.getDouble("max-health", def.maxHealth);
               def.attackDamage = section.getDouble("attack-damage", def.attackDamage);
               def.knockbackResistance = section.getDouble("knockback-resistance", def.knockbackResistance);
               def.knockbackTakenMultiplier = section.getDouble("knockback-taken-multiplier", def.knockbackTakenMultiplier);
               def.movementSpeed = section.getDouble("movement-speed", def.movementSpeed);
               def.followRange = section.getDouble("follow-range", def.followRange);
               def.spawnChance = section.getDouble("spawn-chance", def.spawnChance);
               def.maxConcurrentPerWorld = section.getInt("max-concurrent-per-world", def.maxConcurrentPerWorld);
               def.respawnCooldownSeconds = section.getLong("respawn-cooldown-seconds", def.respawnCooldownSeconds);
               def.abilityCooldownSeconds = section.getLong("ability-cooldown-seconds", def.abilityCooldownSeconds);
               def.stratasReward = section.getDouble("stratas-reward", def.stratasReward);
               def.bonusLoot.clear();
               ConfigurationSection bonus = section.getConfigurationSection("bonus-loot");
               if (bonus != null) {
                  for (String key : bonus.getKeys(false)) {
                     org.bukkit.Material material = Material.matchMaterial(key);
                     if (material == null) plugin.getLogger().warning("Unknown material in " + def.id + " bonus-loot: " + key);
                     else def.bonusLoot.add(LootRoll.fromConfig(bonus.getConfigurationSection(key), material));
                  }
               }
            }
         }
      }
   }

   public Map<String, BossDefinition> all() {
      return this.byId;
   }

   public BossDefinition byId(String id) {
      return this.byId.get(id);
   }
}
