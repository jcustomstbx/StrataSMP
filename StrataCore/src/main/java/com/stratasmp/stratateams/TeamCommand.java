package com.stratasmp.stratateams;

import java.util.List;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TeamCommand implements CommandExecutor, TabCompleter {
   private final TeamManager teams;
   private final TeamGUI gui;

   public TeamCommand(TeamManager teams, TeamGUI gui) {
      this.teams = teams;
      this.gui = gui;
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
      if (sender instanceof Player player) {
         if (args.length == 0) {
            this.gui.open(player);
            return true;
         } else {
            String var6 = args[0].toLowerCase();
            switch (var6) {
               case "create":
                  if (args.length < 2) {
                     player.sendMessage("Usage: /myteam create <name>");
                     return true;
                  }

                  this.reply(player, this.teams.createTeam(player, args[1]), "Team &e" + args[1] + "&a created.");
                  break;
               case "invite":
                  if (args.length < 2) {
                     player.sendMessage("Usage: /myteam invite <player>");
                     return true;
                  }

                  Player target = Bukkit.getPlayer(args[1]);
                  if (target == null) {
                     player.sendMessage("That player isn't online.");
                     return true;
                  }

                  String errorx = this.teams.invite(player, target);
                  if (errorx != null) {
                     player.sendMessage(errorx);
                  } else {
                     player.sendMessage(this.msg("&aInvited " + target.getName() + " to your team."));
                     target.sendMessage(this.msg("&e" + player.getName() + " &finvited you to their team. &a/myteam accept &fto join (expires in 5 minutes)."));
                  }
                  break;
               case "accept":
                  this.reply(player, this.teams.acceptInvite(player), "You joined the team.");
                  break;
               case "deny":
                  boolean had = this.teams.denyInvite(player);
                  player.sendMessage(had ? "Invite declined." : "You don't have a pending team invite.");
                  break;
               case "kick":
                  if (args.length < 2) {
                     player.sendMessage("Usage: /myteam kick <player>");
                     return true;
                  }

                  this.reply(player, this.teams.kick(player, args[1]), args[1] + " was removed from the team.");
                  break;
               case "rename":
                  if (args.length < 2) {
                     player.sendMessage("Usage: /myteam rename <name>");
                     return true;
                  }

                  this.reply(player, this.teams.rename(player, args[1]), "Team renamed to " + args[1] + ".");
                  break;
               case "disband":
                  this.reply(player, this.teams.disband(player), "Team disbanded.");
                  break;
               case "leave":
                  this.reply(player, this.teams.leave(player), "You left the team.");
                  break;
               case "ff":
                  String error = this.teams.toggleFriendlyFire(player);
                  if (error != null) {
                     player.sendMessage(error);
                  } else {
                     Team team = this.teams.getTeam(player.getUniqueId());
                     player.sendMessage(this.msg("&aFriendly fire is now " + (team.friendlyFire ? "&cON" : "&aOFF") + "&a."));
                  }
                  break;
               default:
                  player.sendMessage("Usage: /myteam <create|invite|accept|deny|kick|rename|disband|leave|ff>");
            }

            return true;
         }
      } else {
         sender.sendMessage("Only players can use /myteam.");
         return true;
      }
   }

   private void reply(Player player, String error, String successMessage) {
      player.sendMessage(this.msg(error != null ? error : successMessage));
   }

   private Component msg(String legacy) {
      return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy);
   }

   @Nullable
   public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
      if (args.length == 1) {
         return List.of("create", "invite", "accept", "deny", "kick", "rename", "disband", "leave", "ff");
      } else {
         return args.length != 2 || !args[0].equalsIgnoreCase("invite") && !args[0].equalsIgnoreCase("kick")
            ? List.of()
            : Bukkit.getOnlinePlayers().stream().<String>map(Player::getName).collect(Collectors.toList());
      }
   }
}
