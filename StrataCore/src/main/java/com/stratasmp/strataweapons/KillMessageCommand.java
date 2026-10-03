package com.stratasmp.strataweapons;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KillMessageCommand implements CommandExecutor, TabCompleter {
   private static final String USE_PERMISSION = "strataweapons.killmessage.use";
   private static final String ADMIN_PERMISSION = "strataweapons.killmessage.admin";
   private final KillMessageManager messages;
   private final com.stratasmp.stratacore.StrataModule plugin;

   public KillMessageCommand(com.stratasmp.stratacore.StrataModule plugin, KillMessageManager messages) {
      this.plugin = plugin;
      this.messages = messages;
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
      String sub = args.length == 0 ? "help" : args[0].toLowerCase();
      if (sub.equals("help")) {
         this.sendHelp(sender);
         return true;
      } else if (sub.equals("clear")) {
         if (!sender.hasPermission("strataweapons.killmessage.admin")) {
            sender.sendMessage("You don't have permission to do that.");
            return true;
         } else if (args.length < 2) {
            sender.sendMessage("Usage: /killmsg clear <player>");
            return true;
         } else {
            com.stratasmp.stratacore.NameLookup.resolve(this.plugin, args[1], (uuid, name) -> {
               this.messages.reset(uuid);
               sender.sendMessage("Cleared " + name + "'s custom kill message.");
            }, () -> sender.sendMessage("No player found: " + args[1]));
            return true;
         }
      } else if (sender instanceof Player player) {
         if (!player.hasPermission("strataweapons.killmessage.use")) {
            player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cCustom kill messages aren't unlocked on your account yet."));
            return true;
         } else {
            switch (sub) {
               case "set": {
                  if (args.length < 2) {
                     player.sendMessage("Usage: /killmsg set <message containing {killer} and {victim}>");
                     return true;
                  }

                  String template = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                  String error = this.messages.validate(template);
                  if (error != null) {
                     player.sendMessage(Component.text(error, NamedTextColor.RED));
                     return true;
                  }

                  this.messages.set(player.getUniqueId(), template);
                  player.sendMessage(Component.text("Your kill message is now: ", NamedTextColor.GREEN).append(Component.text(template, NamedTextColor.WHITE)));
                  break;
               }
               case "reset":
                  this.messages.reset(player.getUniqueId());
                  player.sendMessage(Component.text("Your kill message has been reset to the default.", NamedTextColor.GREEN));
                  break;
               case "preview": {
                  String template = this.messages.templateFor(player.getUniqueId());
                  String preview = template.replace("{killer}", player.getName()).replace("{victim}", "SomePlayer").replace("{weapon}", "Weapon");
                  player.sendMessage(Component.text("Preview: ", NamedTextColor.GRAY).append(Component.text(preview, NamedTextColor.WHITE)));
                  break;
               }
               default:
                  this.sendHelp(player);
            }

            return true;
         }
      } else {
         sender.sendMessage("Only players can use /killmsg " + sub + ".");
         return true;
      }
   }

   private void sendHelp(CommandSender sender) {
      boolean unlocked = !(sender instanceof Player p && !p.hasPermission("strataweapons.killmessage.use"));
      sender.sendMessage(Component.text("Custom Kill Messages", NamedTextColor.GOLD));
      sender.sendMessage(
         Component.text("/killmsg set <message>", NamedTextColor.GRAY).append(Component.text("  - set your custom kill broadcast", NamedTextColor.WHITE))
      );
      sender.sendMessage(
         Component.text("/killmsg preview", NamedTextColor.GRAY).append(Component.text("        - see what your message looks like", NamedTextColor.WHITE))
      );
      sender.sendMessage(
         Component.text("/killmsg reset", NamedTextColor.GRAY).append(Component.text("          - revert to the default message", NamedTextColor.WHITE))
      );
      sender.sendMessage(Component.empty());
      sender.sendMessage(Component.text("Placeholders (filled in by the server automatically,", NamedTextColor.YELLOW));
      sender.sendMessage(Component.text("you can't type someone else's name into these):", NamedTextColor.YELLOW));
      sender.sendMessage(
         ((TextComponent)Component.text("  {killer}", NamedTextColor.GRAY).append(Component.text(" - your name ", NamedTextColor.WHITE)))
            .append(Component.text("(required)", NamedTextColor.DARK_GRAY))
      );
      sender.sendMessage(
         ((TextComponent)Component.text("  {victim}", NamedTextColor.GRAY).append(Component.text(" - the player you killed ", NamedTextColor.WHITE)))
            .append(Component.text("(required)", NamedTextColor.DARK_GRAY))
      );
      sender.sendMessage(
         ((TextComponent)Component.text("  {weapon}", NamedTextColor.GRAY).append(Component.text(" - the weapon's name ", NamedTextColor.WHITE)))
            .append(Component.text("(optional)", NamedTextColor.DARK_GRAY))
      );
      sender.sendMessage(Component.empty());
      sender.sendMessage(
         Component.text("Example: ", NamedTextColor.GRAY).append(Component.text("/killmsg set {victim} was gooned on by {killer}", NamedTextColor.WHITE))
      );
      sender.sendMessage(Component.text("Max " + this.messages.maxLength() + " characters. No color codes. Some words are blocked.", NamedTextColor.DARK_GRAY));
      if (sender.hasPermission("strataweapons.killmessage.admin")) {
         sender.sendMessage(
            Component.text("/killmsg clear <player>", NamedTextColor.GRAY)
               .append(Component.text("  - wipe another player's custom message (staff)", NamedTextColor.WHITE))
         );
      }

      if (!unlocked) {
         sender.sendMessage(Component.text("You don't have this unlocked yet - check the store!", NamedTextColor.RED));
      }
   }

   @Nullable
   public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
      if (args.length == 1) {
         List<String> subs = sender.hasPermission("strataweapons.killmessage.admin")
            ? List.of("set", "reset", "preview", "help", "clear")
            : List.of("set", "reset", "preview", "help");
         return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
      } else {
         return args.length == 2 && args[0].equalsIgnoreCase("clear")
            ? Bukkit.getOnlinePlayers().stream().<String>map(Player::getName).collect(Collectors.toList())
            : List.of();
      }
   }
}
