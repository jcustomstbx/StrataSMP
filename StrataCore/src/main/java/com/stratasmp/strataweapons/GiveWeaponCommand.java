package com.stratasmp.strataweapons;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GiveWeaponCommand implements CommandExecutor, TabCompleter {
   private final WeaponCatalog catalog;

   public GiveWeaponCommand(WeaponCatalog catalog) {
      this.catalog = catalog;
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
      if (sender instanceof Player player && !player.isOp()) {
         sender.sendMessage("Only ops or console can use this.");
         return true;
      } else if (args.length < 2) {
         sender.sendMessage("Usage: /givecw <key> <player>");
         return true;
      } else {
         String key = args[0];
         if (!this.catalog.has(key) || this.catalog.isArmorSkin(key)) {
            sender.sendMessage("No weapon defined with key '" + key + "'.");
            return true;
         } else {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
               sender.sendMessage(args[1] + " isn't online.");
               return true;
            } else {
               ItemStack item = this.catalog.build(key);
               HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(new ItemStack[]{item});
               if (!leftover.isEmpty()) {
                  target.getWorld().dropItemNaturally(target.getLocation(), item);
               }

               target.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&aYou received a special weapon!"));
               sender.sendMessage("Gave '" + key + "' to " + target.getName() + ".");
               return true;
            }
         }
      }
   }

   @Nullable
   public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
      if (args.length == 1) {
         return List.copyOf(this.catalog.weaponKeys());
      } else {
         return args.length == 2 ? Bukkit.getOnlinePlayers().stream().<String>map(Player::getName).collect(Collectors.toList()) : List.of();
      }
   }
}
