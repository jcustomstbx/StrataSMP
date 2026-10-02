package com.stratasmp.strataduels.kit;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import com.stratasmp.stratacore.StrataModule;

public class KitManager {
   public static final int MAX_KITS = 6;
   private final StrataModule plugin;
   private final File file;
   private final Map<Integer, ItemStack[]> contents = new HashMap<>();
   private final Map<Integer, ItemStack[]> armor = new HashMap<>();
   private final Map<Integer, ItemStack> offhand = new HashMap<>();

   public KitManager(StrataModule plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "kits.yml");
      this.load();
   }

   private void load() {
      if (this.file.exists()) {
         YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.file);

         for (int slot = 1; slot <= MAX_KITS; slot++) {
            ConfigurationSection section = cfg.getConfigurationSection(String.valueOf(slot));
            if (section != null) {
               ItemStack[] items = new ItemStack[36];
               ConfigurationSection contentsSection = section.getConfigurationSection("contents");
               if (contentsSection != null) {
                  for (String key : contentsSection.getKeys(false)) {
                     int index = Integer.parseInt(key);
                     if (index >= 0 && index < items.length) {
                        items[index] = (ItemStack)contentsSection.get(key);
                     }
                  }
               }

               this.contents.put(slot, items);
               ItemStack[] armorItems = new ItemStack[4];
               ConfigurationSection armorSection = section.getConfigurationSection("armor");
               if (armorSection != null) {
                  armorItems[0] = (ItemStack)armorSection.get("boots");
                  armorItems[1] = (ItemStack)armorSection.get("leggings");
                  armorItems[2] = (ItemStack)armorSection.get("chestplate");
                  armorItems[3] = (ItemStack)armorSection.get("helmet");
               }

               this.armor.put(slot, armorItems);
               if (section.contains("offhand")) {
                  this.offhand.put(slot, (ItemStack)section.get("offhand"));
               }
            }
         }
      }
   }

   public static java.util.List<String> slotNames() {
      java.util.List<String> names = new java.util.ArrayList<>();
      for (int i = 1; i <= MAX_KITS; i++) {
         names.add(String.valueOf(i));
      }
      return names;
   }

   public void saveKitFromPlayer(int slot, Player player) {
      PlayerInventory inv = player.getInventory();
      this.contents.put(slot, (ItemStack[])inv.getStorageContents().clone());
      this.armor.put(slot, (ItemStack[])inv.getArmorContents().clone());
      this.offhand.put(slot, inv.getItemInOffHand().clone());
      this.persist();
   }

   public void clearKit(int slot) {
      this.contents.remove(slot);
      this.armor.remove(slot);
      this.offhand.remove(slot);
      this.persist();
   }

   public boolean hasKit(int slot) {
      return this.contents.containsKey(slot);
   }

   public ItemStack previewItem(int slot) {
      ItemStack[] armorItems = this.armor.get(slot);
      if (armorItems != null && armorItems[3] != null) {
         return armorItems[3];
      } else {
         ItemStack[] items = this.contents.get(slot);
         if (items != null) {
            for (ItemStack item : items) {
               if (item != null) {
                  return item;
               }
            }
         }

         return null;
      }
   }

   public void applyKit(int slot, Player player) {
      PlayerInventory inv = player.getInventory();
      inv.clear();
      inv.setArmorContents(new ItemStack[4]);
      inv.setItemInOffHand(null);
      ItemStack[] savedContents = this.contents.get(slot);
      if (savedContents != null) {
         int storageSize = inv.getStorageContents().length;
         for (int i = 0; i < Math.min(savedContents.length, storageSize); i++) {
            ItemStack item = savedContents[i];
            inv.setItem(i, item == null ? null : item.clone());
         }
      }

      ItemStack[] savedArmor = this.armor.get(slot);
      if (savedArmor != null) {
         ItemStack[] armorCopies = new ItemStack[savedArmor.length];
         for (int i = 0; i < savedArmor.length; i++) {
            armorCopies[i] = savedArmor[i] == null ? null : savedArmor[i].clone();
         }
         inv.setArmorContents(armorCopies);
      }

      ItemStack savedOffhand = this.offhand.get(slot);
      if (savedOffhand != null) {
         inv.setItemInOffHand(savedOffhand.clone());
      }
   }

   private void persist() {
      YamlConfiguration cfg = new YamlConfiguration();

      for (int slot : this.contents.keySet()) {
         String base = String.valueOf(slot);
         ItemStack[] items = this.contents.get(slot);

         for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
               cfg.set(base + ".contents." + i, items[i]);
            }
         }

         ItemStack[] armorItems = this.armor.get(slot);
         if (armorItems != null) {
            if (armorItems[0] != null) {
               cfg.set(base + ".armor.boots", armorItems[0]);
            }

            if (armorItems[1] != null) {
               cfg.set(base + ".armor.leggings", armorItems[1]);
            }

            if (armorItems[2] != null) {
               cfg.set(base + ".armor.chestplate", armorItems[2]);
            }

            if (armorItems[3] != null) {
               cfg.set(base + ".armor.helmet", armorItems[3]);
            }
         }

         ItemStack offhandItem = this.offhand.get(slot);
         if (offhandItem != null) {
            cfg.set(base + ".offhand", offhandItem);
         }
      }

      try {
         cfg.save(this.file);
      } catch (IOException var8) {
         this.plugin.getLogger().warning("Couldn't save kits.yml: " + var8.getMessage());
      }
   }
}
