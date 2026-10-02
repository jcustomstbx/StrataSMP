package com.stratasmp.stratammo.listeners;

import com.stratasmp.stratammo.ActiveUltimates;
import com.stratasmp.stratammo.PerkCooldowns;
import com.stratasmp.stratammo.PerkSettings;
import com.stratasmp.stratammo.Skill;
import com.stratasmp.stratammo.XpNotifier;
import com.stratasmp.stratammo.XpValues;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionData;

public class AlchemyListener implements Listener {
   private final XpValues xpValues;
   private final XpNotifier notifier;
   private final PerkSettings perks;
   private final PerkCooldowns cooldowns;
   private final ActiveUltimates activeUltimates;
   private final Plugin plugin;
   private final Map<Location, UUID> lastTouchedBy = new HashMap<>();

   public AlchemyListener(XpValues xpValues, XpNotifier notifier, PerkSettings perks, PerkCooldowns cooldowns, ActiveUltimates activeUltimates, Plugin plugin) {
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
   public void onClick(InventoryClickEvent event) {
      if (event.getInventory() instanceof BrewerInventory brewer) {
         if (event.getWhoClicked() instanceof Player player) {
            Location location = brewer.getLocation();
            if (!this.notifier.accepts(player)) return;
            if (location != null) {
               this.lastTouchedBy.put(location, player.getUniqueId());
               if (this.perks.enabled()
                  && this.activeUltimates.isActive(player.getUniqueId(), Skill.ALCHEMY)
                  && location.getBlock().getState() instanceof BrewingStand stand) {
                  stand.setBrewingTime(1);
                  stand.update(true);
               }
            }
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onBrew(BrewEvent event) {
      UUID brewerId = this.lastTouchedBy.remove(event.getBlock().getLocation());
      if (!this.notifier.accepts(event.getBlock().getLocation())) return;
      if (brewerId != null) {
         Player player = Bukkit.getPlayer(brewerId);
         if (player != null) {
            for (ItemStack result : event.getResults()) {
               if (result != null && result.getType() != Material.AIR) {
                  this.notifier.award(player, Skill.ALCHEMY, this.xpValues.alchemy(result.getType()));
                  if (result.getItemMeta() instanceof PotionMeta potion && potion.getBasePotionType() != null) {
                     this.notifier.quest(player, com.stratasmp.stratammo.quests.ObjectiveType.BREW_POTION, potion.getBasePotionType().name(), 1);
                  }
               }
            }

            if (this.perks.enabled()) {
               int level = this.notifier.levelOf(player, Skill.ALCHEMY);
               if (level >= 25) {
                  this.practicedBrewer(player, event);
               }

               if (level >= 50) {
                  this.potentMixture(player, event);
               }
            }
         }
      }
   }

   private void practicedBrewer(Player player, BrewEvent event) {
      if (ThreadLocalRandom.current().nextInt(100) < this.perks.alchemyPracticedBrewerChance()) {
         if (this.cooldowns.tryTrigger(player.getUniqueId(), "ALCHEMY:practiced", this.perks.minorBuffCooldownSeconds())) {
            BrewerInventory contents = event.getContents();
            ItemStack ingredient = contents.getIngredient();
            if (ingredient != null && ingredient.getAmount() < ingredient.getMaxStackSize()) {
               ingredient.setAmount(ingredient.getAmount() + 1);
               contents.setIngredient(ingredient);
            }
         }
      }
   }

   private void potentMixture(Player player, BrewEvent event) {
      if (ThreadLocalRandom.current().nextInt(100) < this.perks.alchemyPotentMixtureChance()) {
         if (this.cooldowns.tryTrigger(player.getUniqueId(), "ALCHEMY:potent", this.perks.utilityCooldownSeconds())) {
            for (ItemStack result : event.getResults()) {
               if (result != null && result.getItemMeta() instanceof PotionMeta meta) {
                  PotionData data = meta.getBasePotionData();
                  if (!data.isUpgraded() && data.getType().isUpgradeable()) {
                     meta.setBasePotionData(new PotionData(data.getType(), false, true));
                     result.setItemMeta(meta);
                  }
               }
            }
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onCraft(CraftItemEvent event) {
      if (this.perks.enabled()) {
         if (event.getWhoClicked() instanceof Player player) {
            if (!this.notifier.accepts(player)) return;
            ItemStack result = event.getCurrentItem();
            if (result != null) {
               if (result.getType() == Material.SPLASH_POTION || result.getType() == Material.LINGERING_POTION) {
                  if (this.notifier.levelOf(player, Skill.ALCHEMY) >= 75) {
                     if (ThreadLocalRandom.current().nextInt(100) < this.perks.alchemyWasteNotChance()) {
                        if (this.cooldowns.tryTrigger(player.getUniqueId(), "ALCHEMY:wastenot", this.perks.conservationCooldownSeconds())) {
                           CraftingInventory craftingInv = event.getInventory();
                           int before = this.totalItems(craftingInv.getMatrix());
                           Material refund = result.getType() == Material.SPLASH_POTION ? Material.GUNPOWDER : Material.DRAGON_BREATH;
                           Bukkit.getScheduler().runTask(this.plugin, () -> {
                              int after = this.totalItems(craftingInv.getMatrix());
                              if (after < before) {
                                 player.getInventory().addItem(new ItemStack[]{new ItemStack(refund, 1)});
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

   private int totalItems(ItemStack[] matrix) {
      int total = 0;

      for (ItemStack item : matrix) {
         if (item != null) {
            total += item.getAmount();
         }
      }

      return total;
   }
}
