package com.stratasmp.strataweapons;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class WeaponAnvilListener implements Listener {
   private static final int REPAIR_COST_LEVELS = 3;
   private final WeaponCatalog catalog;

   public WeaponAnvilListener(WeaponCatalog catalog) {
      this.catalog = catalog;
   }

   // vanilla refuses Protection + Blast Protection (Sharpness + Smite, ...); this listener replaces the
   // vanilla result for skinned items, so it has to keep that rule itself
   private static boolean conflictsWithExisting(Enchantment incoming, ItemMeta target) {
      for (Enchantment present : target.getEnchants().keySet()) {
         if (!present.equals(incoming) && incoming.conflictsWith(present)) {
            return true;
         }
      }
      return false;
   }

   @EventHandler
   public void onPrepareAnvil(PrepareAnvilEvent event) {
      AnvilInventory anvil = event.getInventory();
      ItemStack left = anvil.getItem(0);
      ItemStack right = anvil.getItem(1);
      if (left != null && right != null) {
         if (this.catalog.weaponKeyOf(left) != null) {
            if (right.hasItemMeta() && right.getItemMeta().hasEnchants()) {
               ItemMeta leftMeta = left.getItemMeta();
               Map<Enchantment, Integer> toApply = new LinkedHashMap<>();

               for (Entry<Enchantment, Integer> entry : right.getItemMeta().getEnchants().entrySet()) {
                  Enchantment enchant = entry.getKey();
                  if (enchant.canEnchantItem(left) && !conflictsWithExisting(enchant, leftMeta)) {
                     int existing = leftMeta.getEnchantLevel(enchant);
                     toApply.put(enchant, Math.max(existing, entry.getValue()));
                  }
               }

               if (!toApply.isEmpty()) {
                  ItemStack result = left.clone();
                  ItemMeta resultMeta = result.getItemMeta();

                  for (Entry<Enchantment, Integer> entryx : toApply.entrySet()) {
                     resultMeta.addEnchant(entryx.getKey(), entryx.getValue(), true);
                  }

                  result.setItemMeta(resultMeta);
                  event.setResult(result);
                  anvil.setRepairCost(3);
               }
            }
         }
      }
   }
}
