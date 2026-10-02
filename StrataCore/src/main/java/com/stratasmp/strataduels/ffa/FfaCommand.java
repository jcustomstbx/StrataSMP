package com.stratasmp.strataduels.ffa;

import com.stratasmp.stratacore.StrataModule;
import com.stratasmp.strataduels.gui.KitSelectGUI;
import com.stratasmp.strataduels.kit.KitManager;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FfaCommand implements CommandExecutor, TabCompleter {
   private final StrataModule plugin;
   private final FfaManager ffa;
   private final KitManager kits;
   private final KitSelectGUI kitSelectGUI;

   public FfaCommand(StrataModule plugin, FfaManager ffa, KitManager kits, KitSelectGUI kitSelectGUI) {
      this.plugin = plugin;
      this.ffa = ffa;
      this.kits = kits;
      this.kitSelectGUI = kitSelectGUI;
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
      if (!sender.hasPermission("strataduels.use")) {
         sender.sendMessage("You don't have permission to use the FFA.");
         return true;
      }
      boolean admin = sender.hasPermission("strataduels.admin");
      String sub = args.length == 0 ? "join" : args[0].toLowerCase();

      if (sub.equals("start") || sub.equals("stop")) {
         if (!admin) {
            sender.sendMessage("You don't have permission to do that.");
            return true;
         }
         String error = sub.equals("start") ? this.ffa.forceStart() : this.ffa.forceStop();
         sender.sendMessage(error != null ? error : (sub.equals("start") ? "Starting the FFA now." : "Ending the FFA."));
         return true;
      }
      if (sub.equals("status")) {
         sender.sendMessage(this.statusLine());
         return true;
      }
      if (!(sender instanceof Player player)) {
         sender.sendMessage("Only players can use /ffa.");
         return true;
      }
      if (this.plugin.getConfig().getBoolean("maintenance-mode", false) && !admin) {
         player.sendMessage("Duels and the FFA are down for maintenance right now - check back shortly.");
         return true;
      }

      switch (sub) {
         case "join", "queue", "q" -> this.handleJoin(player, args);
         case "leave", "cancel" -> {
            String result = this.ffa.leave(player.getUniqueId());
            player.sendMessage(result != null ? result : "You're not in the FFA.");
         }
         default -> player.sendMessage("Usage: /ffa [join <kit>|leave|status]");
      }
      return true;
   }

   private void handleJoin(Player player, String[] args) {
      int forced = this.ffa.forcedKit();
      List<Integer> allowed = this.ffa.allowedKits();
      if (forced > 0) {
         this.joinWithKit(player, forced);
      } else if (allowed.isEmpty()) {
         player.sendMessage("No FFA kits are set up yet - ask an admin.");
      } else if (args.length >= 2) {
         int kit;
         try {
            kit = Integer.parseInt(args[1]);
         } catch (NumberFormatException e) {
            player.sendMessage("Pick a kit number: " + allowed + ".");
            return;
         }
         if (!allowed.contains(kit)) {
            player.sendMessage("That kit isn't available in the FFA - pick one of: " + allowed + ".");
            return;
         }
         this.joinWithKit(player, kit);
      } else if (allowed.size() == 1) {
         this.joinWithKit(player, allowed.get(0));
      } else {
         this.kitSelectGUI.open(player, allowed, this.ffa.kitNames(), kit -> this.joinWithKit(player, kit));
      }
   }

   private void joinWithKit(Player player, int kit) {
      String error = this.ffa.join(player, kit);
      if (error != null) {
         player.sendMessage(error);
         return;
      }
      player.sendMessage(Component.text("Joined the FFA queue" + this.ffa.joinSummary(), NamedTextColor.GREEN));
   }

   private String statusLine() {
      return switch (this.ffa.state()) {
         case WAITING -> "FFA: " + (this.ffa.lobbySeconds() >= 0 ? "starting in " + this.ffa.lobbySeconds() + "s" : "waiting for players") + " - "
            + this.ffa.queueSize() + " queued, " + this.ffa.neededToStart() + " needed.";
         case COUNTDOWN -> "FFA: round starting - " + this.ffa.fightersLeft() + " fighters, " + this.ffa.queueSize() + " queued for the next.";
         case ACTIVE -> "FFA: round in progress - " + this.ffa.fightersLeft() + " fighters left, " + this.ffa.queueSize() + " queued for the next.";
         case ENDING -> "FFA: round ending - " + this.ffa.queueSize() + " queued for the next.";
      };
   }

   @Nullable
   public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
      if (args.length == 1) {
         List<String> base = new ArrayList<>(List.of("join", "leave", "status"));
         if (sender.hasPermission("strataduels.admin")) {
            base.add("start");
            base.add("stop");
         }
         return base;
      }
      if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
         List<String> names = new ArrayList<>();
         for (int kit : this.ffa.allowedKits()) {
            names.add(String.valueOf(kit));
         }
         return names;
      }
      return List.of();
   }
}
