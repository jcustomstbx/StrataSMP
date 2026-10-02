package com.stratasmp.strataweapons;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

public class TransferEnchantsCommand implements CommandExecutor {
   private final WeaponCatalog catalog;

   public TransferEnchantsCommand(WeaponCatalog catalog) {
      this.catalog = catalog;
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
      if (!(sender instanceof Player player)) {
         sender.sendMessage("Only players can use this command.");
         return true;
      } else {
         PlayerInventory inv = player.getInventory();
         ItemStack customWeapon = inv.getItemInMainHand();
         ItemStack donor = inv.getItemInOffHand();
         if (this.catalog.weaponKeyOf(customWeapon) == null) {
            player.sendMessage(Component.text("Hold one of your custom weapons in your main hand.", NamedTextColor.RED));
            return true;
         } else if (donor.getType().isAir()) {
            player.sendMessage(Component.text("Hold the enchanted item you want to transfer from in your off-hand.", NamedTextColor.RED));
            return true;
         } else if (donor.hasItemMeta() && donor.getItemMeta().hasEnchants()) {
            ItemMeta weaponMeta = customWeapon.getItemMeta();
            Map<Enchantment, Integer> toApply = new LinkedHashMap<>();

            for (Entry<Enchantment, Integer> entry : donor.getItemMeta().getEnchants().entrySet()) {
               Enchantment enchant = entry.getKey();
               if (enchant.canEnchantItem(customWeapon)) {
                  int existing = weaponMeta.getEnchantLevel(enchant);
                  toApply.put(enchant, Math.max(existing, entry.getValue()));
               }
            }

            if (toApply.isEmpty()) {
               player.sendMessage(Component.text("None of that item's enchantments can apply to a sword.", NamedTextColor.RED));
               return true;
            } else {
               for (Entry<Enchantment, Integer> entryx : toApply.entrySet()) {
                  weaponMeta.addEnchant(entryx.getKey(), entryx.getValue(), true);
               }

               customWeapon.setItemMeta(weaponMeta);
               inv.setItemInOffHand(new ItemStack(Material.AIR));
               StringBuilder names = new StringBuilder();

               for (Enchantment enchant : toApply.keySet()) {
                  if (!names.isEmpty()) {
                     names.append(", ");
                  }

                  names.append(enchant.getKey().getKey());
               }

               player.sendMessage(Component.text("Transferred: " + names + " onto your weapon. The donor item was consumed.", NamedTextColor.GREEN));
               return true;
            }
         } else {
            player.sendMessage(Component.text("That off-hand item doesn't have any enchantments to transfer.", NamedTextColor.RED));
            return true;
         }
      }
   }
}
