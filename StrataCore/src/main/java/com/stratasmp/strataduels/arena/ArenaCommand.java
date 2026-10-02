package com.stratasmp.strataduels.arena;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ArenaCommand implements CommandExecutor, TabCompleter {
   private final ArenaManager arenas;
   private final ArenaResetManager resets;

   public ArenaCommand(ArenaManager arenas, ArenaResetManager resets) {
      this.arenas = arenas;
      this.resets = resets;
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
      if (!sender.hasPermission("strataduels.admin")) {
         sender.sendMessage("You don't have permission to manage duel arenas.");
         return true;
      } else if (!(sender instanceof Player player)) {
         sender.sendMessage("Only players can use /duelarena.");
         return true;
      } else if (args.length == 0) {
         player.sendMessage("Usage: /duelarena <create|setpos1|setpos2|setbound1|setbound2|savebaseline|cleanup|list|delete|toggle|ffa|addspawn|clearspawns> [name]");
         return true;
      } else {
         String var6 = args[0].toLowerCase();
         switch (var6) {
            case "create":
               if (args.length < 2) {
                  player.sendMessage("Usage: /duelarena create <name>");
                  return true;
               }

               this.reply(
                  player,
                  this.arenas.createArena(args[1]),
                  "Arena " + args[1] + " created - stand where a duelist should spawn and run /duelarena setpos1 " + args[1] + "."
               );
               break;
            case "setpos1":
               if (args.length < 2) {
                  player.sendMessage("Usage: /duelarena setpos1 <name>");
                  return true;
               }

               this.reply(player, this.arenas.setPos(args[1], 1, player), "Position 1 for " + args[1] + " set to your current location.");
               break;
            case "setpos2":
               if (args.length < 2) {
                  player.sendMessage("Usage: /duelarena setpos2 <name>");
                  return true;
               }

               this.reply(player, this.arenas.setPos(args[1], 2, player), "Position 2 for " + args[1] + " set to your current location.");
               break;
            case "setbound1":
               if (args.length < 2) {
                  player.sendMessage("Usage: /duelarena setbound1 <name>");
                  return true;
               }

               this.reply(
                  player,
                  this.arenas.setBound(args[1], 1, player),
                  "Bound corner 1 for " + args[1] + " set - stand at the opposite corner of the playable area and run setbound2, then savebaseline."
               );
               break;
            case "setbound2":
               if (args.length < 2) {
                  player.sendMessage("Usage: /duelarena setbound2 <name>");
                  return true;
               }

               this.reply(
                  player,
                  this.arenas.setBound(args[1], 2, player),
                  "Bound corner 2 for " + args[1] + " set - run /duelarena savebaseline " + args[1] + " to lock in the clean state to reset to."
               );
               break;
            case "savebaseline":
               if (args.length < 2) {
                  player.sendMessage("Usage: /duelarena savebaseline <name>");
                  return true;
               }

               Arena targetx = this.arenas.get(args[1]);
               if (targetx == null) {
                  player.sendMessage("No arena named " + args[1] + ".");
                  return true;
               }

               Arena.Bounds savedBounds = targetx.computeBounds();
               this.reply(
                  player,
                  this.resets.captureBaseline(targetx),
                  "Baseline saved for "
                     + args[1]
                     + " - covers Y "
                     + savedBounds.minY()
                     + " to "
                     + savedBounds.maxY()
                     + ". Anything placed above or below that (cobwebs included) won't get cleared after a match - re-run setbound1/setbound2 at the room's actual floor and ceiling corners if that's not tall enough."
               );
               break;
            case "cleanup":
               if (args.length < 2) {
                  player.sendMessage("Usage: /duelarena cleanup <name>");
                  return true;
               }

               Arena target = this.arenas.get(args[1]);
               if (target == null) {
                  player.sendMessage("No arena named " + args[1] + ".");
                  return true;
               }

               if (!target.hasBounds()) {
                  player.sendMessage("No bounds set for " + args[1] + " yet - run setbound1/setbound2 first.");
                  return true;
               }

               int itemsRemoved = this.resets.clearDroppedItems(target);
               if (this.resets.hasBaseline(target)) {
                  this.resets.resetArena(target);
                  player.sendMessage("Cleaned " + args[1] + " - reverted to its saved baseline and removed " + itemsRemoved + " dropped item(s).");
               } else {
                  player.sendMessage(
                     "Removed "
                        + itemsRemoved
                        + " dropped item(s) from "
                        + args[1]
                        + " - no baseline saved yet, so blocks weren't touched. Run savebaseline to enable that too."
                  );
               }
               break;
            case "delete":
               if (args.length < 2) {
                  player.sendMessage("Usage: /duelarena delete <name>");
                  return true;
               }

               this.reply(player, this.arenas.deleteArena(args[1]), "Arena " + args[1] + " deleted.");
               break;
            case "toggle":
               if (args.length < 2) {
                  player.sendMessage("Usage: /duelarena toggle <name>");
                  return true;
               }

               this.reply(player, this.arenas.toggleEnabled(args[1]), "Toggled " + args[1] + ".");
               break;
            case "ffa":
               if (args.length < 2) {
                  player.sendMessage("Usage: /duelarena ffa <name> - turns the arena into a free-for-all map (run again to turn it back into a 1v1 arena).");
                  return true;
               }

               String ffaError = this.arenas.toggleFfa(args[1]);
               if (ffaError != null) {
                  player.sendMessage(ffaError);
                  return true;
               }
               Arena ffaArena = this.arenas.get(args[1]);
               if (!ffaArena.ffa) {
                  player.sendMessage(ffaArena.name + " is a normal 1v1 arena again.");
                  return true;
               }
               player.sendMessage(ffaArena.name + " is now an FFA map - it is no longer used for 1v1 duels. " + this.ffaChecklist(ffaArena));
               break;
            case "addspawn":
               if (args.length < 2) {
                  player.sendMessage("Usage: /duelarena addspawn <name> - adds your position as an extra FFA spawn.");
                  return true;
               }

               String spawnError = this.arenas.addSpawn(args[1], player);
               if (spawnError != null) {
                  player.sendMessage(spawnError);
                  return true;
               }
               Arena spawnArena = this.arenas.get(args[1]);
               player.sendMessage("Spawn added to " + spawnArena.name + " - it now has " + spawnArena.ffaSpawns().size() + " FFA spawn(s) counting pos1 and pos2.");
               break;
            case "clearspawns":
               if (args.length < 2) {
                  player.sendMessage("Usage: /duelarena clearspawns <name>");
                  return true;
               }

               this.reply(player, this.arenas.clearSpawns(args[1]), "Cleared the extra spawns on " + args[1] + " (pos1 and pos2 still count as spawns).");
               break;
            case "list":
               List<Arena> all = this.arenas.list();
               if (all.isEmpty()) {
                  player.sendMessage("No duel arenas configured yet.");
                  return true;
               }

               player.sendMessage("Duel arenas:");

               for (Arena arena : all) {
                  String status = arena.enabled ? (arena.isReady() ? "ready" : "incomplete") : "disabled";
                  String resetStatus = this.resets.hasBaseline(arena) ? "resets after matches" : "no reset baseline saved";
                  String mode = arena.ffa ? "FFA, " + arena.ffaSpawns().size() + " spawns, " : "";
                  player.sendMessage(" - " + arena.name + " (" + mode + status + ", " + resetStatus + ")");
               }
               break;
            default:
               player.sendMessage("Usage: /duelarena <create|setpos1|setpos2|setbound1|setbound2|savebaseline|list|delete|toggle> [name]");
         }

         return true;
      }
   }

   private void reply(Player player, String error, String successMessage) {
      player.sendMessage(error != null ? error : successMessage);
   }

   /** What an FFA map still needs before /ffa will run on it, so an admin can see the next step at a glance. */
   private String ffaChecklist(Arena arena) {
      List<String> missing = new java.util.ArrayList<>();
      if (!arena.hasPos1 || !arena.hasPos2) {
         missing.add("setpos1/setpos2 (the first two spawns)");
      }
      if (!arena.hasBounds()) {
         missing.add("setbound1/setbound2");
      }
      if (!this.resets.hasBaseline(arena)) {
         missing.add("savebaseline");
      }
      if (arena.ffaSpawns().size() < 2) {
         missing.add("at least two spawns");
      }
      return missing.isEmpty()
         ? "It is ready - players can use /ffa. Add more spawns with /duelarena addspawn " + arena.name + "."
         : "Still needed before /ffa works: " + String.join(", ", missing) + ".";
   }

   @Nullable
   public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
      if (args.length == 1) {
         return List.of("create", "setpos1", "setpos2", "setbound1", "setbound2", "savebaseline", "cleanup", "list", "delete", "toggle", "ffa", "addspawn", "clearspawns");
      } else {
         return args.length == 2 && !args[0].equalsIgnoreCase("create") ? this.arenas.list().stream().map(a -> a.name).toList() : List.of();
      }
   }
}
