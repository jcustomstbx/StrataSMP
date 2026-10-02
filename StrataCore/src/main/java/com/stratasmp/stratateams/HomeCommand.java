package com.stratasmp.stratateams;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class HomeCommand implements CommandExecutor {
   private final TeamManager teams;
   private final HomeTeleporter teleporter;

   public HomeCommand(TeamManager teams, HomeTeleporter teleporter) {
      this.teams = teams;
      this.teleporter = teleporter;
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
      if (sender instanceof Player player) {
         if (args.length > 0 && args[0].equalsIgnoreCase("set")) {
            String error = this.teams.setHome(player);
            player.sendMessage(error != null ? error : "Team home set to your current location.");
            return true;
         } else {
            this.teleporter.startTeleport(player);
            return true;
         }
      } else {
         sender.sendMessage("Only players can use this command.");
         return true;
      }
   }
}
