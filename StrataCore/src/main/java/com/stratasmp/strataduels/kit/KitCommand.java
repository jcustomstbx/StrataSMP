package com.stratasmp.strataduels.kit;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KitCommand implements CommandExecutor, TabCompleter {
   private final KitManager kits;

   public KitCommand(KitManager kits) {
      this.kits = kits;
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
      if (!sender.hasPermission("strataduels.admin")) {
         sender.sendMessage("You don't have permission to manage duel kits.");
         return true;
      } else if (sender instanceof Player player) {
         if (args.length < 2) {
            player.sendMessage("Usage: /duelkit <set|clear> <1-" + KitManager.MAX_KITS + ">");
            return true;
         } else {
            Integer slot = this.parseSlot(args[1]);
            if (slot == null) {
               player.sendMessage("Kit slot must be a number from 1 to " + KitManager.MAX_KITS + ".");
               return true;
            } else {
               String var7 = args[0].toLowerCase();
               switch (var7) {
                  case "set":
                     this.kits.saveKitFromPlayer(slot, player);
                     player.sendMessage("Kit " + slot + " saved from your current inventory and armor.");
                     break;
                  case "clear":
                     this.kits.clearKit(slot);
                     player.sendMessage("Kit " + slot + " cleared.");
                     break;
                  default:
                     player.sendMessage("Usage: /duelkit <set|clear> <1-" + KitManager.MAX_KITS + ">");
               }

               return true;
            }
         }
      } else {
         sender.sendMessage("Only players can use /duelkit.");
         return true;
      }
   }

   private Integer parseSlot(String raw) {
      try {
         int value = Integer.parseInt(raw);
         return value >= 1 && value <= KitManager.MAX_KITS ? value : null;
      } catch (NumberFormatException var31) {
         return null;
      }
   }

   @Nullable
   public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
      if (args.length == 1) {
         return List.of("set", "clear");
      } else {
         return args.length == 2 ? KitManager.slotNames() : List.of();
      }
   }
}
