package com.stratasmp.stratammo.commands;

import com.stratasmp.stratammo.DataManager;
import com.stratasmp.stratammo.LevelCurve;
import com.stratasmp.stratammo.PlayerData;
import com.stratasmp.stratammo.SalvageArtist;
import com.stratasmp.stratammo.Skill;
import com.stratasmp.stratammo.UltimatePerks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class MmoCommand implements CommandExecutor, TabCompleter {
   private static final Map<Skill, String> ULTIMATE_NAMES = Map.of(
      Skill.MINING,
      "Seismic Shift",
      Skill.WOODCUTTING,
      "Timber Fall",
      Skill.FARMING,
      "Nature's Wrath",
      Skill.FISHING,
      "Tidal Pull",
      Skill.ENCHANTING,
      "Arcane Surge",
      Skill.REPAIR,
      "Masterforge",
      Skill.ALCHEMY,
      "Grand Alchemy"
   );
   private final DataManager dataManager;
   private final LevelCurve levelCurve;
   private final UltimatePerks ultimatePerks;
   private final SalvageArtist salvageArtist;

   public MmoCommand(DataManager dataManager, LevelCurve levelCurve, UltimatePerks ultimatePerks, SalvageArtist salvageArtist) {
      this.dataManager = dataManager;
      this.levelCurve = levelCurve;
      this.ultimatePerks = ultimatePerks;
      this.salvageArtist = salvageArtist;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (args.length == 0) {
         return this.showOwnStats(sender);
      } else {
         String sub = args[0].toLowerCase();
         if (sub.equals("stats")) {
            return args.length > 1 ? this.showStatsByName(sender, args[1]) : this.showOwnStats(sender);
         } else if (sub.equals("top")) {
            String skillArg = args.length > 1 ? args[1] : null;
            return this.showTop(sender, skillArg);
         } else if (sub.equals("help")) {
            return this.showHelp(sender);
         } else if (sub.equals("ultimate")) {
            return this.useUltimate(sender, args.length > 1 ? args[1] : null);
         } else if (sub.equals("salvage")) {
            if (sender instanceof Player player) {
               this.salvageArtist.salvage(player);
            } else {
               sender.sendMessage(Component.text("Only players can salvage gear.", NamedTextColor.RED));
            }

            return true;
         } else if (sub.equals("resetall")) {
            if (!sender.hasPermission("stratammo.admin")) {
               sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
               return true;
            } else {
               this.dataManager.resetAll();
               sender.sendMessage(Component.text("Every player's MMO skills have been reset.", NamedTextColor.GREEN));
               return true;
            }
         } else {
            sender.sendMessage(Component.text("Unknown command. Try /mmo help", NamedTextColor.RED));
            return true;
         }
      }
   }

   private boolean useUltimate(CommandSender sender, String skillArg) {
      if (sender instanceof Player player) {
         Skill skill = this.matchSkill(skillArg);
         if (skill != null && ULTIMATE_NAMES.containsKey(skill)) {
            this.ultimatePerks.activate(player, skill, ULTIMATE_NAMES.get(skill));
            return true;
         } else {
            sender.sendMessage(Component.text("Usage: /mmo ultimate <skill>", NamedTextColor.RED));
            return true;
         }
      } else {
         sender.sendMessage(Component.text("Only players can use ultimates.", NamedTextColor.RED));
         return true;
      }
   }

   private boolean showHelp(CommandSender sender) {
      sender.sendMessage(Component.text("--- StrataMMO Commands ---", NamedTextColor.GOLD));
      sender.sendMessage(Component.text("/mmo", NamedTextColor.YELLOW).append(Component.text(" - your own skill levels", NamedTextColor.WHITE)));
      sender.sendMessage(
         Component.text("/mmo stats <player>", NamedTextColor.YELLOW).append(Component.text(" - someone else's skill levels", NamedTextColor.WHITE))
      );
      sender.sendMessage(Component.text("/mmo top", NamedTextColor.YELLOW).append(Component.text(" - top 10 by total levels", NamedTextColor.WHITE)));
      sender.sendMessage(Component.text("/mmo top <skill>", NamedTextColor.YELLOW).append(Component.text(" - top 10 for one skill", NamedTextColor.WHITE)));
      sender.sendMessage(
         Component.text("/mmo ultimate <skill>", NamedTextColor.YELLOW).append(Component.text(" - activate a level 100 ultimate", NamedTextColor.WHITE))
      );
      sender.sendMessage(
         Component.text("/mmo salvage", NamedTextColor.YELLOW)
            .append(Component.text(" - break down held gear for materials (Repair 75)", NamedTextColor.WHITE))
      );
      StringBuilder skills = new StringBuilder();

      for (Skill skill : Skill.values()) {
         if (skills.length() > 0) {
            skills.append(", ");
         }

         skills.append(skill.displayName());
      }

      sender.sendMessage(Component.text("Skills: " + skills, NamedTextColor.GRAY));
      return true;
   }

   private boolean showOwnStats(CommandSender sender) {
      if (sender instanceof Player player) {
         PlayerData data = this.dataManager.get(player.getUniqueId());
         if (data == null) {
            sender.sendMessage(Component.text("No StrataMMO data for you yet.", NamedTextColor.RED));
            return true;
         } else {
            this.printStats(sender, data);
            return true;
         }
      } else {
         sender.sendMessage(Component.text("Usage: /mmo stats <player>", NamedTextColor.RED));
         return true;
      }
   }

   private boolean showStatsByName(CommandSender sender, String targetName) {
      PlayerData data = this.dataManager.findByName(targetName);
      if (data == null) {
         sender.sendMessage(Component.text("No StrataMMO data for " + targetName, NamedTextColor.RED));
         return true;
      } else {
         this.printStats(sender, data);
         return true;
      }
   }

   private void printStats(CommandSender sender, PlayerData data) {
      sender.sendMessage(Component.text("--- " + data.lastKnownName + "'s StrataMMO Stats ---", NamedTextColor.GOLD));

      for (Skill skill : Skill.values()) {
         int[] levelXp = this.levelCurve.levelFromTotalXp(data.getXp(skill));
         int needed = this.levelCurve.xpForLevel(levelXp[0]);
         String line = skill.displayName() + ": Level " + levelXp[0] + " (" + levelXp[1] + "/" + needed + " xp)";
         if (this.isTopOfSkill(data, skill)) {
            line = line + " ★ #1 on server";
         }

         sender.sendMessage(Component.text(line, NamedTextColor.YELLOW));
      }
   }

   private boolean showTop(CommandSender sender, String skillArg) {
      List<PlayerData> snapshot = this.dataManager.getLeaderboardSnapshot();
      if (skillArg == null) {
         List<PlayerData> ranked = snapshot.stream().sorted(Comparator.comparingInt(this::totalLevels).reversed()).limit(10L).collect(Collectors.toList());
         sender.sendMessage(Component.text("--- Top Overall (all skills) ---", NamedTextColor.GOLD));
         int rank = 1;

         for (PlayerData data : ranked) {
            String prefix = rank == 1 ? "★ " : rank + ". ";
            sender.sendMessage(Component.text(prefix + data.lastKnownName + " - " + this.totalLevels(data) + " total levels", NamedTextColor.YELLOW));
            rank++;
         }

         return true;
      } else {
         Skill skill = this.matchSkill(skillArg);
         if (skill == null) {
            sender.sendMessage(Component.text("Unknown skill. Use /mmo help to see the list.", NamedTextColor.RED));
            return true;
         } else {
            List<PlayerData> ranked = snapshot.stream()
               .sorted(Comparator.<PlayerData>comparingInt(d -> d.getXp(skill)).reversed())
               .limit(10L)
               .collect(Collectors.toList());
            sender.sendMessage(Component.text("--- Top " + skill.displayName() + " ---", NamedTextColor.GOLD));
            int rank = 1;

            for (PlayerData data : ranked) {
               int level = this.levelCurve.levelFromTotalXp(data.getXp(skill))[0];
               String prefix = rank == 1 ? "★ " : rank + ". ";
               sender.sendMessage(Component.text(prefix + data.lastKnownName + " - Level " + level, NamedTextColor.YELLOW));
               rank++;
            }

            return true;
         }
      }
   }

   private boolean isTopOfSkill(PlayerData data, Skill skill) {
      int xp = data.getXp(skill);
      if (xp <= 0) {
         return false;
      } else {
         for (PlayerData other : this.dataManager.getLeaderboardSnapshot()) {
            if (other.getXp(skill) > xp) {
               return false;
            }
         }

         return true;
      }
   }

   private int totalLevels(PlayerData data) {
      int sum = 0;

      for (Skill skill : Skill.values()) {
         sum += this.levelCurve.levelFromTotalXp(data.getXp(skill))[0];
      }

      return sum;
   }

   private Skill matchSkill(String arg) {
      for (Skill skill : Skill.values()) {
         if (skill.name().equalsIgnoreCase(arg) || skill.displayName().equalsIgnoreCase(arg)) {
            return skill;
         }
      }

      return null;
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      List<String> options = new ArrayList<>();
      if (args.length == 1) {
         options.add("stats");
         options.add("top");
         options.add("help");
         options.add("ultimate");
         options.add("salvage");
      } else if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
         for (Skill skill : Skill.values()) {
            options.add(skill.name().toLowerCase());
         }
      } else if (args.length == 2 && args[0].equalsIgnoreCase("ultimate")) {
         for (Skill skill : ULTIMATE_NAMES.keySet()) {
            options.add(skill.name().toLowerCase());
         }
      }

      return options;
   }
}
