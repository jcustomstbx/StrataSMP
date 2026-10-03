package com.stratasmp.strataduels.match;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlayerSnapshot {
   private final ItemStack[] contents;
   private final ItemStack[] armor;
   private final ItemStack offhand;
   private final int xpLevel;
   private final float xpProgress;
   private final int totalExperience;
   private final double health;
   private final int foodLevel;
   private final float saturation;
   private final Location location;
   private final GameMode gameMode;

   private PlayerSnapshot(
      ItemStack[] contents,
      ItemStack[] armor,
      ItemStack offhand,
      int xpLevel,
      float xpProgress,
      int totalExperience,
      double health,
      int foodLevel,
      float saturation,
      Location location,
      GameMode gameMode
   ) {
      this.contents = contents;
      this.armor = armor;
      this.offhand = offhand;
      this.xpLevel = xpLevel;
      this.xpProgress = xpProgress;
      this.totalExperience = totalExperience;
      this.health = health;
      this.foodLevel = foodLevel;
      this.saturation = saturation;
      this.location = location;
      this.gameMode = gameMode;
   }

   public static PlayerSnapshot capture(Player player) {
      PlayerInventory inv = player.getInventory();
      return new PlayerSnapshot(
         (ItemStack[])inv.getStorageContents().clone(),
         (ItemStack[])inv.getArmorContents().clone(),
         inv.getItemInOffHand().clone(),
         player.getLevel(),
         player.getExp(),
         player.getTotalExperience(),
         player.getHealth(),
         player.getFoodLevel(),
         player.getSaturation(),
         player.getLocation().clone(),
         player.getGameMode()
      );
   }

   public Location location() {
      return this.location;
   }

   public void applyState(Player player) {
      PlayerInventory inv = player.getInventory();
      inv.clear();
      inv.setStorageContents(this.contents);
      inv.setArmorContents(this.armor);
      inv.setItemInOffHand(this.offhand);
      for (org.bukkit.potion.PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
         player.removePotionEffect(effect.getType());
      }
      player.setAbsorptionAmount(0.0);
      player.setFireTicks(0);
      player.setLevel(this.xpLevel);
      player.setExp(this.xpProgress);
      player.setTotalExperience(this.totalExperience);
      player.setGameMode(this.gameMode);
      double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
      player.setHealth(Math.min(this.health, maxHealth));
      player.setFoodLevel(this.foodLevel);
      player.setSaturation(this.saturation);
      player.updateInventory();
   }

   /** Persisted when a player disconnects before this could be applied - see MatchManager.restoreIfOnline. */
   public void saveTo(YamlConfiguration cfg) {
      cfg.set("contents", new ArrayList<>(Arrays.asList(this.contents)));
      cfg.set("armor", new ArrayList<>(Arrays.asList(this.armor)));
      cfg.set("offhand", this.offhand);
      cfg.set("xpLevel", this.xpLevel);
      cfg.set("xpProgress", (double)this.xpProgress);
      cfg.set("totalExperience", this.totalExperience);
      cfg.set("health", this.health);
      cfg.set("foodLevel", this.foodLevel);
      cfg.set("saturation", (double)this.saturation);
      cfg.set("location", this.location);
      cfg.set("gameMode", this.gameMode.name());
   }

   public static PlayerSnapshot loadFrom(YamlConfiguration cfg) {
      List<?> rawContents = cfg.getList("contents", List.of());
      List<?> rawArmor = cfg.getList("armor", List.of());
      return new PlayerSnapshot(
         rawContents.stream().map(o -> (ItemStack)o).toArray(ItemStack[]::new),
         rawArmor.stream().map(o -> (ItemStack)o).toArray(ItemStack[]::new),
         (ItemStack)cfg.get("offhand"),
         cfg.getInt("xpLevel"),
         (float)cfg.getDouble("xpProgress"),
         cfg.getInt("totalExperience"),
         cfg.getDouble("health"),
         cfg.getInt("foodLevel"),
         (float)cfg.getDouble("saturation"),
         (Location)cfg.get("location"),
         GameMode.valueOf(cfg.getString("gameMode", "SURVIVAL"))
      );
   }
}
