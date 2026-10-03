package com.stratasmp.stratammo.listeners;

import com.stratasmp.stratammo.ActiveUltimates;
import com.stratasmp.stratammo.PerkSettings;
import com.stratasmp.stratammo.Skill;
import com.stratasmp.stratammo.XpNotifier;
import com.stratasmp.stratammo.XpValues;
import java.util.Map;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class RepairListener implements Listener {
   private final NamespacedKey stacksKey;
   private final NamespacedKey poolKey;
   private final XpValues xpValues;
   private final XpNotifier notifier;
   private final PerkSettings perks;
   private final ActiveUltimates activeUltimates;

   public RepairListener(XpValues xpValues, XpNotifier notifier, PerkSettings perks, ActiveUltimates activeUltimates, Plugin plugin) {
      this.xpValues = xpValues;
      this.notifier = notifier;
      this.perks = perks;
      this.activeUltimates = activeUltimates;
      this.stacksKey = new NamespacedKey(plugin, "reinforced_stacks");
      this.poolKey = new NamespacedKey(plugin, "reinforced_pool");
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onPrepare(PrepareAnvilEvent event) {
      if (this.perks.enabled()) {
         if (event.getView().getPlayer() instanceof Player player) {
            if (!this.notifier.accepts(player)) return;
            if (this.notifier.levelOf(player, Skill.REPAIR) >= 25) {
               AnvilInventory anvil = event.getInventory();
               int cost = anvil.getRepairCost();
               if (cost > 0) {
                  int reduced = Math.max(1, cost - Math.max(1, cost * this.perks.repairSteadyHandsCostReductionPercent() / 100));
                  anvil.setRepairCost(reduced);
               }
            }
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onClick(InventoryClickEvent event) {
      if (event.getSlotType() == SlotType.RESULT) {
         if (event.getInventory() instanceof AnvilInventory anvil) {
            if (event.getWhoClicked() instanceof Player player) {
               if (!this.notifier.accepts(player)) return;
               // a click with something already on the cursor doesn't pick the result up, so it earns nothing
               if (!event.isShiftClick() && event.getCursor() != null && !event.getCursor().getType().isAir()) return;
               ItemStack result = event.getCurrentItem();
               if (result != null && !result.getType().isAir()) {
                  if (this.perks.enabled() && this.activeUltimates.isActive(player.getUniqueId(), Skill.REPAIR)) {
                     this.masterforge(event, anvil, player);
                  } else {
                     int cost = anvil.getRepairCost();
                     if (cost > 0) {
                        if (player.getGameMode() == GameMode.CREATIVE || player.getLevel() >= cost) {
                           this.notifier.award(player, Skill.REPAIR, this.xpValues.repair(cost));
                           if (this.perks.enabled()) {
                              if (this.notifier.levelOf(player, Skill.REPAIR) >= 50) {
                                 this.reinforcedBuild(result);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void masterforge(InventoryClickEvent event, AnvilInventory anvil, Player player) {
      event.setCancelled(true);
      ItemStack beingRepaired = anvil.getItem(0);
      if (beingRepaired != null) {
         int cost = anvil.getRepairCost();
         if (cost > 0) {
            this.notifier.award(player, Skill.REPAIR, this.xpValues.repair(cost));
         }

         ItemStack repaired = beingRepaired.clone();
         ItemMeta meta = repaired.getItemMeta();
         if (meta instanceof Damageable damageable) {
            damageable.setDamage(0);
            repaired.setItemMeta(meta);
         }

         anvil.setItem(0, null);
         anvil.setRepairCost(0);
         Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack[]{repaired});

         for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
         }

         player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6F, 1.5F);
      }
   }

   private void reinforcedBuild(ItemStack result) {
      ItemMeta meta = result.getItemMeta();
      if (meta instanceof Damageable) {
         int maxDurability = result.getType().getMaxDurability();
         if (maxDurability > 0) {
            int stacks = (Integer)meta.getPersistentDataContainer().getOrDefault(this.stacksKey, PersistentDataType.INTEGER, 0);
            if (stacks < this.perks.repairReinforcedBuildMaxStacks()) {
               int bonus = Math.max(1, maxDurability * this.perks.repairReinforcedBuildBonusPercent() / 100);
               int pool = (Integer)meta.getPersistentDataContainer().getOrDefault(this.poolKey, PersistentDataType.INTEGER, 0);
               meta.getPersistentDataContainer().set(this.stacksKey, PersistentDataType.INTEGER, stacks + 1);
               meta.getPersistentDataContainer().set(this.poolKey, PersistentDataType.INTEGER, pool + bonus);
               result.setItemMeta(meta);
            }
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onItemDamage(PlayerItemDamageEvent event) {
      if (!this.notifier.accepts(event.getPlayer())) return;
      if (this.perks.enabled()) {
         ItemStack item = event.getItem();
         ItemMeta meta = item.getItemMeta();
         if (meta != null) {
            int pool = (Integer)meta.getPersistentDataContainer().getOrDefault(this.poolKey, PersistentDataType.INTEGER, 0);
            if (pool > 0) {
               if (pool >= event.getDamage()) {
                  meta.getPersistentDataContainer().set(this.poolKey, PersistentDataType.INTEGER, pool - event.getDamage());
                  event.setCancelled(true);
               } else {
                  event.setDamage(event.getDamage() - pool);
                  meta.getPersistentDataContainer().set(this.poolKey, PersistentDataType.INTEGER, 0);
               }

               item.setItemMeta(meta);
            }
         }
      }
   }
}
