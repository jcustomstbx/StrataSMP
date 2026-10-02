package com.stratasmp.stratabosses;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public record LootRoll(Material material, double chance, int min, int max) {
   public static LootRoll fromConfig(ConfigurationSection section, Material material) {
      return section == null
         ? new LootRoll(material, 0.0, 0, 0)
         : new LootRoll(material, section.getDouble("chance"), section.getInt("min"), section.getInt("max"));
   }

   public int rollAmount() {
      if (this.max > 0 && !(ThreadLocalRandom.current().nextDouble() >= this.chance)) {
         return this.min >= this.max ? this.min : ThreadLocalRandom.current().nextInt(this.min, this.max + 1);
      } else {
         return 0;
      }
   }
}
