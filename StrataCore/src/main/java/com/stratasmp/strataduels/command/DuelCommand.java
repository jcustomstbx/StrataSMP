package com.stratasmp.strataduels.command;

import com.stratasmp.strataduels.DataManager;
import com.stratasmp.strataduels.DuelPlayerData;
import com.stratasmp.strataduels.RankTier;
import com.stratasmp.strataduels.SeasonManager;
import com.stratasmp.strataduels.challenge.ChallengeManager;
import com.stratasmp.strataduels.gui.DuelMainMenuGUI;
import com.stratasmp.strataduels.gui.KitSelectGUI;
import com.stratasmp.strataduels.gui.LeaderboardGUI;
import com.stratasmp.strataduels.arena.Arena;
import com.stratasmp.strataduels.arena.ArenaManager;
import com.stratasmp.strataduels.kit.KitManager;
import com.stratasmp.strataduels.match.MatchManager;
import com.stratasmp.strataduels.queue.QueueManager;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import com.stratasmp.stratacore.StrataModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DuelCommand implements CommandExecutor, TabCompleter {
   private final StrataModule plugin;
   private final QueueManager queueManager;
   private final ChallengeManager challengeManager;
   private final KitManager kitManager;
   private final DataManager dataManager;
   private final LeaderboardGUI leaderboardGUI;
   private final KitSelectGUI kitSelectGUI;
   private final SeasonManager seasonManager;
   private final DuelMainMenuGUI mainMenuGUI;
   private final MatchManager matchManager;
   private final ArenaManager arenaManager;

   public DuelCommand(
      StrataModule plugin,
      QueueManager queueManager,
      ChallengeManager challengeManager,
      KitManager kitManager,
      DataManager dataManager,
      LeaderboardGUI leaderboardGUI,
      KitSelectGUI kitSelectGUI,
      SeasonManager seasonManager,
      DuelMainMenuGUI mainMenuGUI,
      MatchManager matchManager,
      ArenaManager arenaManager
   ) {
      this.plugin = plugin;
      this.queueManager = queueManager;
      this.challengeManager = challengeManager;
      this.kitManager = kitManager;
      this.dataManager = dataManager;
      this.matchManager = matchManager;
      this.arenaManager = arenaManager;
      this.leaderboardGUI = leaderboardGUI;
      this.kitSelectGUI = kitSelectGUI;
      this.seasonManager = seasonManager;
      this.mainMenuGUI = mainMenuGUI;
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
      if (!sender.hasPermission("strataduels.use")) {
         sender.sendMessage("You don't have permission to use duels.");
         return true;
      } else {
         boolean isAdmin = sender.hasPermission("strataduels.admin");
         if (args.length >= 1 && args[0].equalsIgnoreCase("maintenance")) {
            if (!isAdmin) {
               sender.sendMessage("You don't have permission to do that.");
               return true;
            } else {
               this.toggleMaintenance(sender);
               return true;
            }
         } else if (this.isMaintenanceMode() && !isAdmin) {
            sender.sendMessage("Duels are down for maintenance right now - check back shortly.");
            return true;
         } else if (sender instanceof Player player) {
            if (args.length == 0) {
               this.mainMenuGUI.open(player);
               return true;
            } else {
               String var7 = args[0].toLowerCase();
               switch (var7) {
                  case "queue":
                  case "q":
                     this.handleQueue(player, args);
                     break;
                  case "challenge":
                  case "c":
                     this.handleChallenge(player, args);
                     break;
                  case "accept":
                  case "a":
                     this.handleAccept(player, args);
                     break;
                  case "deny":
                  case "d":
                     boolean had = this.challengeManager.deny(player.getUniqueId());
                     player.sendMessage(had ? "Challenge declined." : "You don't have a pending duel challenge.");
                     break;
                  case "cancel":
                     this.handleCancel(player);
                     break;
                  case "stats":
                     this.handleStats(player, args);
                     break;
                  case "top":
                     this.leaderboardGUI.open(player);
                     break;
                  case "season":
                     this.seasonManager.lastSeasonSummaryLines().forEach(player::sendMessage);
                     break;
                  case "test":
                     if (!isAdmin) {
                        player.sendMessage("You don't have permission to do that.");
                     } else {
                        this.handleTest(player, args);
                     }
                     break;
                  case "end":
                     if (!isAdmin) {
                        player.sendMessage("You don't have permission to do that.");
                     } else {
                        String error = this.matchManager.endTest(player);
                        player.sendMessage(error != null ? error : "Test duel ended.");
                     }
                     break;
                  default:
                     player.sendMessage("Usage: /duel <queue|challenge|accept|deny|cancel|stats|top|season>");
               }

               return true;
            }
         } else {
            sender.sendMessage("Only players can use /duel.");
            return true;
         }
      }
   }

   private boolean isMaintenanceMode() {
      return this.plugin.getConfig().getBoolean("maintenance-mode", false);
   }

   private void toggleMaintenance(CommandSender sender) {
      boolean newState = !this.isMaintenanceMode();
      this.plugin.getConfig().set("maintenance-mode", newState);
      this.plugin.saveConfig();
      sender.sendMessage(newState ? "Duels are now under maintenance - only staff can use /duel." : "Duels are back open to everyone.");
   }

   private void handleQueue(Player player, String[] args) {
      if (this.queueManager.isQueued(player.getUniqueId())) {
         player.sendMessage("You're already queued - use /duel cancel to leave.");
      } else if (args.length <= 1) {
         this.kitSelectGUI.open(player, kitx -> this.queueWithKit(player, kitx));
      } else {
         Integer kit = this.parseKit(player, args, 1);
         if (kit != null) {
            this.queueWithKit(player, kit);
         }
      }
   }

   private void queueWithKit(Player player, int kit) {
      String error = this.queueManager.join(player, kit);
      player.sendMessage(error != null ? error : "Joined the ranked duel queue with kit " + kit + ".");
   }

   private void handleChallenge(Player player, String[] args) {
      if (args.length < 2) {
         player.sendMessage("Usage: /duel challenge <player> [kit]");
      } else {
         Player target = Bukkit.getPlayer(args[1]);
         if (target == null) {
            player.sendMessage("That player isn't online.");
         } else if (args.length <= 2) {
            this.kitSelectGUI.open(player, kitx -> this.challengeWithKit(player, target, kitx));
         } else {
            Integer kit = this.parseKit(player, args, 2);
            if (kit != null) {
               this.challengeWithKit(player, target, kit);
            }
         }
      }
   }

   private void challengeWithKit(Player player, Player target, int kit) {
      String error = this.challengeManager.challenge(player, kit, target);
      if (error != null) {
         player.sendMessage(error);
      } else {
         player.sendMessage(Component.text("Challenged " + target.getName() + " to a duel.", NamedTextColor.GOLD));
         long expirySeconds = this.plugin.getConfig().getLong("challenge.expiry-seconds", 60L);
         target.sendMessage(
            Component.text(
               player.getName() + " challenged you to a duel! /duel accept <kit> or /duel deny (expires in " + expirySeconds + "s).", NamedTextColor.YELLOW
            )
         );
      }
   }

   private void handleAccept(Player player, String[] args) {
      if (args.length <= 1) {
         this.kitSelectGUI.open(player, kitx -> this.acceptWithKit(player, kitx));
      } else {
         Integer kit = this.parseKit(player, args, 1);
         if (kit != null) {
            this.acceptWithKit(player, kit);
         }
      }
   }

   private void acceptWithKit(Player player, int kit) {
      String error = this.challengeManager.accept(player, kit);
      if (error != null) {
         player.sendMessage(error);
      }
   }

   private void handleTest(Player player, String[] args) {
      if (args.length < 2) {
         List<String> names = this.arenaManager.list().stream().map(a -> a.name).collect(Collectors.toList());
         player.sendMessage("Usage: /duel test <arena> [kit] - arenas: " + String.join(", ", names));
         return;
      }
      Arena arena = this.arenaManager.get(args[1]);
      if (arena == null) {
         player.sendMessage("No arena named '" + args[1] + "'.");
         return;
      }
      int kit = 1;
      if (args.length >= 3) {
         Integer parsed = this.parseKit(player, args, 2);
         if (parsed == null) {
            return;
         }
         kit = parsed;
      }
      String error = this.matchManager.startTest(player, arena, kit);
      if (error != null) {
         player.sendMessage(error);
      } else {
         player.sendMessage(
            Component.text("Test duel started at " + arena.name + " - run /duel end whenever you're done.", NamedTextColor.GOLD)
         );
      }
   }

   private void handleCancel(Player player) {
      if (this.queueManager.leave(player.getUniqueId())) {
         player.sendMessage("Left the duel queue.");
      } else if (this.challengeManager.cancelOutgoing(player.getUniqueId())) {
         player.sendMessage("Cancelled your outgoing duel challenge.");
      } else {
         player.sendMessage("Nothing to cancel.");
      }
   }

   private void handleStats(Player player, String[] args) {
      DuelPlayerData data;
      String label;
      if (args.length >= 2) {
         data = this.dataManager.findByName(args[1]);
         label = args[1];
      } else {
         data = this.dataManager.get(player.getUniqueId());
         label = player.getName();
      }

      if (data == null) {
         player.sendMessage(label + " has no duel stats yet.");
      } else {
         ConfigurationSection thresholds = this.plugin.getConfig().getConfigurationSection("rank-tiers");
         RankTier tier = RankTier.forElo(data.seasonElo, thresholds);
         player.sendMessage(Component.text(data.lastKnownName + "'s duel stats", NamedTextColor.GOLD));
         player.sendMessage(Component.text("Season: " + tier.displayName() + " - " + Math.round(data.seasonElo) + " ELO", NamedTextColor.YELLOW));
         player.sendMessage(
            Component.text(
               "Season record: " + data.seasonWins + "W / " + data.seasonLosses + "L, streak " + data.seasonWinStreak + " (best " + data.seasonBestStreak + ")",
               NamedTextColor.GRAY
            )
         );
         player.sendMessage(
            Component.text(
               "Lifetime: " + data.lifetimeWins + "W / " + data.lifetimeLosses + "L over " + data.lifetimeGamesPlayed + " games", NamedTextColor.GRAY
            )
         );
      }
   }

   private Integer parseKit(Player player, String[] args, int index) {
      if (args.length <= index) {
         player.sendMessage("Specify a kit: 1 to " + KitManager.MAX_KITS + ".");
         return null;
      } else {
         int kit;
         try {
            kit = Integer.parseInt(args[index]);
         } catch (NumberFormatException var6) {
            player.sendMessage("Kit must be a number from 1 to " + KitManager.MAX_KITS + ".");
            return null;
         }

         if (kit >= 1 && kit <= KitManager.MAX_KITS && this.kitManager.hasKit(kit)) {
            return kit;
         } else {
            player.sendMessage("Kit " + kit + " isn't set up yet - ask an admin.");
            return null;
         }
      }
   }

   @Nullable
   public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
      if (args.length == 1) {
         List<String> base = List.of("queue", "challenge", "accept", "deny", "cancel", "stats", "top", "season");
         if (sender.hasPermission("strataduels.admin")) {
            base = new ArrayList<>(base);
            base.add("maintenance");
            base.add("test");
            base.add("end");
         }

         return base;
      } else if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
         return this.arenaManager.list().stream().map(a -> a.name).collect(Collectors.toList());
      } else if (args.length != 2 || !args[0].equalsIgnoreCase("challenge") && !args[0].equalsIgnoreCase("c") && !args[0].equalsIgnoreCase("stats")) {
         return args.length == 2
                  && (args[0].equalsIgnoreCase("queue") || args[0].equalsIgnoreCase("q") || args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("a"))
               || args.length == 3 && (args[0].equalsIgnoreCase("challenge") || args[0].equalsIgnoreCase("c"))
               || args.length == 3 && args[0].equalsIgnoreCase("test")
            ? KitManager.slotNames()
            : List.of();
      } else {
         return Bukkit.getOnlinePlayers().stream().<String>map(Player::getName).collect(Collectors.toList());
      }
   }
}
