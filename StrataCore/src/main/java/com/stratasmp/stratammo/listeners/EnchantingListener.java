package com.stratasmp.stratammo.listeners;

import com.stratasmp.stratammo.ActiveUltimates;
import com.stratasmp.stratammo.PerkCooldowns;
import com.stratasmp.stratammo.PerkSettings;
import com.stratasmp.stratammo.Skill;
import com.stratasmp.stratammo.XpNotifier;
import com.stratasmp.stratammo.XpValues;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class EnchantingListener implements Listener {
   private final XpValues xpValues;
   private final XpNotifier notifier;
   private final PerkSettings perks;
   private final PerkCooldowns cooldowns;
   private final ActiveUltimates activeUltimates;
   private final Plugin plugin;

   public EnchantingListener(
      XpValues xpValues, XpNotifier notifier, PerkSettings perks, PerkCooldowns cooldowns, ActiveUltimates activeUltimates, Plugin plugin
   ) {
      this.xpValues = xpValues;
      this.notifier = notifier;
      this.perks = perks;
      this.cooldowns = cooldowns;
      this.activeUltimates = activeUltimates;
      this.plugin = plugin;
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onPrepare(PrepareItemEnchantEvent event) {
      if (!this.notifier.accepts(event.getEnchanter())) return;
      if (this.perks.enabled()) {
         if (this.notifier.levelOf(event.getEnchanter(), Skill.ENCHANTING) >= 25) {
            for (EnchantmentOffer offer : event.getOffers()) {
               if (offer != null) {
                  int boosted = offer.getCost() + Math.max(1, offer.getCost() * this.perks.enchantingAetherialFocusBonusPercent() / 100);
                  offer.setCost(Math.min(30, boosted));
               }
            }
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onEnchant(EnchantItemEvent event) {
      Player player = event.getEnchanter();
      if (!this.notifier.accepts(player)) return;
      int xp = this.xpValues.enchanting(event.getExpLevelCost());
      this.notifier.award(player, Skill.ENCHANTING, xp);
      if (this.perks.enabled()) {
         int level = this.notifier.levelOf(player, Skill.ENCHANTING);
         boolean isTool = this.isTool(event.getItem().getType());
         boolean ultimateActive = this.activeUltimates.isActive(player.getUniqueId(), Skill.ENCHANTING);
         if (!ultimateActive) {
            if (level >= 75) {
               this.resonanceInfusion(player, event, isTool);
            }
         } else {
            int refund = event.getExpLevelCost();
            Bukkit.getScheduler().runTask(this.plugin, () -> player.giveExpLevels(refund));
            if (isTool) {
               for (Entry<Enchantment, Integer> entry : event.getEnchantsToAdd().entrySet()) {
                  entry.setValue(entry.getKey().getMaxLevel());
               }
            }
         }
      }
   }

   private void resonanceInfusion(Player player, EnchantItemEvent event, boolean isTool) {
      if (ThreadLocalRandom.current().nextInt(100) < this.perks.enchantingResonanceInfusionChance()) {
         if (this.cooldowns.tryTrigger(player.getUniqueId(), "ENCHANTING:resonance", this.perks.conservationCooldownSeconds())) {
            if (isTool && ThreadLocalRandom.current().nextBoolean()) {
               Map<Enchantment, Integer> enchants = event.getEnchantsToAdd();
               if (!enchants.isEmpty()) {
                  Enchantment[] keys = enchants.keySet().toArray(new Enchantment[0]);
                  Enchantment picked = keys[ThreadLocalRandom.current().nextInt(keys.length)];
                  int current = enchants.get(picked);
                  if (current < picked.getMaxLevel()) {
                     enchants.put(picked, current + 1);
                  }
               }
            } else {
               int refund = event.getExpLevelCost();
               Bukkit.getScheduler().runTask(this.plugin, () -> player.giveExpLevels(refund));
            }
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onGrindstoneClick(InventoryClickEvent event) {
      if (this.perks.enabled()) {
         if (event.getSlotType() == SlotType.RESULT) {
            if (event.getInventory() instanceof GrindstoneInventory grindstone) {
               if (event.getWhoClicked() instanceof Player player) {
                  if (!this.notifier.accepts(player)) return;
                  if (this.notifier.levelOf(player, Skill.ENCHANTING) >= 50) {
                     ItemStack result = event.getCurrentItem();
                     if (result != null && !result.getType().isAir()) {
                        int enchantLevels = 0;

                        for (int slot = 0; slot <= 1; slot++) {
                           ItemStack item = grindstone.getItem(slot);
                           if (item != null) {
                              for (int lvl : item.getEnchantments().values()) {
                                 enchantLevels += lvl;
                              }
                           }
                        }

                        if (enchantLevels > 0) {
                           int bonus = Math.max(1, enchantLevels / 2);
                           Bukkit.getScheduler().runTask(this.plugin, () -> {
                              ItemStack slot0 = grindstone.getItem(0);
                              ItemStack slot1 = grindstone.getItem(1);
                              boolean consumed = (slot0 == null || slot0.getType().isAir()) && (slot1 == null || slot1.getType().isAir());
                              if (consumed) {
                                 player.giveExpLevels(bonus);
                              }
                           });
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isTool(Material material) {
      String name = material.name();
      return name.endsWith("_PICKAXE")
         || name.endsWith("_AXE")
         || name.endsWith("_HOE")
         || name.endsWith("_SHOVEL")
         || material == Material.FISHING_ROD
         || material == Material.SHEARS;
   }
}
