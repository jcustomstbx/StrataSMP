package com.stratasmp.stratabosses;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /boss list | tp <number> | kill <number|all> | reload */
final class BossAdminCommand implements CommandExecutor, TabCompleter {
   private final BossManager manager;

   BossAdminCommand(BossManager manager) {
      this.manager = manager;
   }

   @Override
   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!sender.hasPermission("stratabosses.admin")) {
         sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
         return true;
      }
      String sub = args.length > 0 ? args[0].toLowerCase() : "list";
      List<BossManager.BossInfo> bosses = this.manager.activeBosses();
      switch (sub) {
         case "list" -> {
            if (bosses.isEmpty()) sender.sendMessage(Component.text("No active bosses.", NamedTextColor.GRAY));
            for (int i = 0; i < bosses.size(); i++) {
               BossManager.BossInfo b = bosses.get(i);
               sender.sendMessage(Component.text((i + 1) + ". " + b.displayName() + " - " + b.location().getWorld().getName() + " "
                  + b.location().getBlockX() + ", " + b.location().getBlockY() + ", " + b.location().getBlockZ()
                  + " (" + Math.round(b.health()) + " HP)", NamedTextColor.YELLOW));
            }
         }
         case "tp" -> {
            BossManager.BossInfo b = pick(sender, bosses, args);
            if (b == null) return true;
            if (sender instanceof Player player) player.teleport(b.location());
            else sender.sendMessage(Component.text("Only players can teleport.", NamedTextColor.RED));
         }
         case "kill" -> {
            if (args.length > 1 && args[1].equalsIgnoreCase("all")) {
               bosses.forEach(b -> this.manager.removeBoss(b.id()));
               sender.sendMessage(Component.text("Removed " + bosses.size() + " boss(es).", NamedTextColor.GREEN));
            } else {
               BossManager.BossInfo b = pick(sender, bosses, args);
               if (b == null) return true;
               this.manager.removeBoss(b.id());
               sender.sendMessage(Component.text("Removed " + b.displayName() + ".", NamedTextColor.GREEN));
            }
         }
         case "reload" -> {
            this.manager.reload();
            sender.sendMessage(Component.text("Boss stats, loot and rewards reloaded. Spawn timing needs a restart.", NamedTextColor.GREEN));
         }
         default -> sender.sendMessage(Component.text("/boss <list|tp <n>|kill <n|all>|reload>", NamedTextColor.YELLOW));
      }
      return true;
   }

   private BossManager.BossInfo pick(CommandSender sender, List<BossManager.BossInfo> bosses, String[] args) {
      try {
         int index = Integer.parseInt(args[1]) - 1;
         if (index >= 0 && index < bosses.size()) return bosses.get(index);
      } catch (RuntimeException ignored) {
         // falls through to the usage message
      }
      sender.sendMessage(Component.text("Pick a boss number from /boss list.", NamedTextColor.RED));
      return null;
   }

   @Override
   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      return args.length == 1 ? List.of("list", "tp", "kill", "reload") : List.of();
   }
}
