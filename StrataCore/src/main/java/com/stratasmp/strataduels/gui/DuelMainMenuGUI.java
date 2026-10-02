package com.stratasmp.strataduels.gui;

import com.stratasmp.strataduels.DataManager;
import com.stratasmp.strataduels.DuelPlayerData;
import com.stratasmp.strataduels.RankTier;
import com.stratasmp.strataduels.SeasonManager;
import com.stratasmp.strataduels.challenge.ChallengeManager;
import com.stratasmp.strataduels.challenge.PendingChallenge;
import com.stratasmp.strataduels.ffa.FfaManager;
import com.stratasmp.strataduels.match.MatchManager;
import com.stratasmp.strataduels.queue.QueueManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import com.stratasmp.stratacore.StrataModule;

public class DuelMainMenuGUI implements Listener {
   private static final int SLOT_QUEUE = 10;
   private static final int SLOT_CHALLENGE = 12;
   private static final int SLOT_INCOMING = 14;
   private static final int SLOT_DENY = 16;
   private static final int SLOT_STATS = 20;
   private static final int SLOT_LEADERBOARD = 22;
   private static final int SLOT_SEASON = 24;
   private static final int SLOT_CANCEL = 31;
   private static final int SLOT_FFA = 30;
   private final StrataModule plugin;
   private final QueueManager queueManager;
   private final ChallengeManager challengeManager;
   private final MatchManager matchManager;
   private final DataManager dataManager;
   private final KitSelectGUI kitSelectGUI;
   private final LeaderboardGUI leaderboardGUI;
   private final DuelPlayerPickerGUI playerPickerGUI;
   private final SeasonManager seasonManager;
   private final FfaManager ffaManager;
   private final Map<UUID, Inventory> openGuis = new HashMap<>();

   public DuelMainMenuGUI(
      StrataModule plugin,
      QueueManager queueManager,
      ChallengeManager challengeManager,
      MatchManager matchManager,
      DataManager dataManager,
      KitSelectGUI kitSelectGUI,
      LeaderboardGUI leaderboardGUI,
      DuelPlayerPickerGUI playerPickerGUI,
      SeasonManager seasonManager,
      FfaManager ffaManager
   ) {
      this.ffaManager = ffaManager;
      this.plugin = plugin;
      this.queueManager = queueManager;
      this.challengeManager = challengeManager;
      this.matchManager = matchManager;
      this.dataManager = dataManager;
      this.kitSelectGUI = kitSelectGUI;
      this.leaderboardGUI = leaderboardGUI;
      this.playerPickerGUI = playerPickerGUI;
      this.seasonManager = seasonManager;
   }

   public void open(Player viewer) {
      if (this.matchManager.isInMatch(viewer.getUniqueId())) {
         viewer.sendMessage("You're currently in a duel.");
      } else if (this.ffaManager.isFighter(viewer.getUniqueId())) {
         viewer.sendMessage("You're in the FFA right now - use /ffa leave to drop out.");
      } else {
         UUID uuid = viewer.getUniqueId();
         boolean queued = this.queueManager.isQueued(uuid);
         PendingChallenge incoming = this.challengeManager.getIncoming(uuid);
         PendingChallenge outgoing = this.challengeManager.getOutgoing(uuid);
         Inventory inventory = Bukkit.createInventory(null, 36, Component.text("Duels"));

         for (int i = 0; i < 36; i++) {
            inventory.setItem(i, this.filler());
         }

         inventory.setItem(10, this.queueIcon(queued));
         inventory.setItem(12, this.challengeIcon());
         inventory.setItem(14, this.incomingIcon(incoming));
         if (incoming != null) {
            inventory.setItem(16, this.denyIcon(incoming));
         }

         if (outgoing != null) {
            inventory.setItem(31, this.cancelIcon(outgoing));
         }

         inventory.setItem(SLOT_FFA, this.ffaIcon(this.ffaManager.isQueued(uuid)));
         inventory.setItem(20, this.statsIcon(viewer));
         inventory.setItem(22, this.leaderboardIcon());
         inventory.setItem(24, this.seasonIcon());
         this.openGuis.put(uuid, inventory);
         viewer.openInventory(inventory);
      }
   }

   private ItemStack queueIcon(boolean queued) {
      ItemStack item = new ItemStack(queued ? Material.REDSTONE : Material.DIAMOND_SWORD);
      ItemMeta meta = item.getItemMeta();
      if (queued) {
         meta.displayName(this.name("Leave Queue", NamedTextColor.RED));
         meta.lore(this.lore("You're in the ranked queue.", "Click to leave."));
      } else {
         meta.displayName(this.name("Join Ranked Queue", NamedTextColor.GREEN));
         meta.lore(this.lore("Get auto-matched by rating.", "Click to pick a kit."));
      }

      item.setItemMeta(meta);
      return item;
   }

   private ItemStack ffaIcon(boolean queued) {
      ItemStack item = new ItemStack(queued ? Material.REDSTONE : Material.NETHERITE_AXE);
      ItemMeta meta = item.getItemMeta();
      if (queued) {
         meta.displayName(this.name("Leave FFA Queue", NamedTextColor.RED));
         meta.lore(this.lore("You're queued for the free-for-all.", this.ffaStatus(), "Click to leave."));
      } else if (!this.ffaManager.available()) {
         meta.displayName(this.name("Free-For-All", NamedTextColor.GRAY));
         meta.lore(this.lore("No FFA map is set up yet."));
      } else {
         meta.displayName(this.name("Join Free-For-All", NamedTextColor.GOLD));
         meta.lore(this.lore("Everyone fights at once - last one standing wins.", this.ffaStatus(), "Click to pick a kit and join."));
      }
      item.setItemMeta(meta);
      return item;
   }

   private String ffaStatus() {
      int seconds = this.ffaManager.lobbySeconds();
      if (this.ffaManager.state() != FfaManager.State.WAITING) {
         return "Round in progress - " + this.ffaManager.queueSize() + " queued for the next.";
      }
      return (seconds >= 0 ? "Starts in " + seconds + "s - " : "Waiting for players - ") + this.ffaManager.queueSize() + " queued.";
   }

   private ItemStack challengeIcon() {      ItemStack item = new ItemStack(Material.PLAYER_HEAD);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(this.name("Challenge a Player", NamedTextColor.GOLD));
      meta.lore(this.lore("Pick someone online to duel.", "Click to choose a target."));
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack incomingIcon(PendingChallenge incoming) {
      ItemStack item = new ItemStack(incoming != null ? Material.LIME_DYE : Material.GRAY_DYE);
      ItemMeta meta = item.getItemMeta();
      if (incoming != null) {
         meta.displayName(this.name("Incoming Challenge!", NamedTextColor.GREEN));
         meta.lore(this.lore("From " + this.nameOf(incoming.challenger()) + ".", "Click to accept."));
      } else {
         meta.displayName(this.name("No Incoming Challenge", NamedTextColor.GRAY));
         meta.lore(this.lore("Nobody has challenged you right now."));
      }

      item.setItemMeta(meta);
      return item;
   }

   private ItemStack denyIcon(PendingChallenge incoming) {
      ItemStack item = new ItemStack(Material.RED_DYE);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(this.name("Deny Challenge", NamedTextColor.RED));
      meta.lore(this.lore("Decline " + this.nameOf(incoming.challenger()) + "'s challenge."));
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack cancelIcon(PendingChallenge outgoing) {
      ItemStack item = new ItemStack(Material.BARRIER);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(this.name("Cancel Challenge", NamedTextColor.RED));
      meta.lore(this.lore("Waiting on " + this.nameOf(outgoing.target()) + ".", "Click to cancel."));
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack statsIcon(Player viewer) {
      ItemStack item = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta meta = (SkullMeta)item.getItemMeta();
      meta.setOwningPlayer(viewer);
      meta.displayName(this.name("My Stats", NamedTextColor.AQUA));
      DuelPlayerData data = this.dataManager.get(viewer.getUniqueId());
      List<Component> lore = new ArrayList<>();
      if (data == null) {
         lore.add(this.line("No duel stats yet."));
      } else {
         ConfigurationSection thresholds = this.plugin.getConfig().getConfigurationSection("rank-tiers");
         RankTier tier = RankTier.forElo(data.seasonElo, thresholds);
         lore.add(this.line(tier.displayName() + " - " + Math.round(data.seasonElo) + " ELO"));
         lore.add(this.line("Season: " + data.seasonWins + "W / " + data.seasonLosses + "L, streak " + data.seasonWinStreak));
         lore.add(this.line("Lifetime: " + data.lifetimeWins + "W / " + data.lifetimeLosses + "L"));
      }

      meta.lore(lore);
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack leaderboardIcon() {
      ItemStack item = new ItemStack(Material.GOLDEN_APPLE);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(this.name("Leaderboard", NamedTextColor.YELLOW));
      meta.lore(this.lore("View the top ranked duelists.", "Click to open."));
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack seasonIcon() {
      ItemStack item = new ItemStack(Material.CLOCK);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(this.name("Season Info", NamedTextColor.LIGHT_PURPLE));
      meta.lore(this.lore("View last season's results.", "Click to view."));
      item.setItemMeta(meta);
      return item;
   }

   private String nameOf(UUID uuid) {
      OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
      String name = offlinePlayer.getName();
      return name != null ? name : "Unknown";
   }

   private Component name(String text, NamedTextColor color) {
      return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
   }

   private List<Component> lore(String... lines) {
      List<Component> lore = new ArrayList<>();

      for (String line : lines) {
         lore.add(this.line(line));
      }

      return lore;
   }

   private Component line(String text) {
      return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
   }

   private ItemStack filler() {
      ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(Component.text(" "));
      item.setItemMeta(meta);
      return item;
   }

   private void close(Player player) {
      this.openGuis.remove(player.getUniqueId());
      player.closeInventory();
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         Inventory inventory = this.openGuis.get(player.getUniqueId());
         if (inventory != null && event.getInventory() == inventory) {
            event.setCancelled(true);
            switch (event.getRawSlot()) {
               case 10:
                  this.handleQueueClick(player);
               case 11:
               case 13:
               case 15:
               case 17:
               case 18:
               case 19:
               case 20:
               case 21:
               case 23:
               case 25:
               case 26:
               case 27:
               case 28:
               case 29:
               default:
                  break;
               case 12:
                  this.handleChallengeClick(player);
                  break;
               case 14:
                  this.handleAcceptClick(player);
                  break;
               case 16:
                  this.handleDenyClick(player);
                  break;
               case 22:
                  this.handleLeaderboardClick(player);
                  break;
               case 24:
                  this.handleSeasonClick(player);
                  break;
               case 30:
                  this.handleFfaClick(player);
                  break;
               case 31:
                  this.handleCancelClick(player);
            }
         }
      }
   }

   private void handleQueueClick(Player player) {
      if (this.queueManager.isQueued(player.getUniqueId())) {
         this.queueManager.leave(player.getUniqueId());
         player.sendMessage("Left the duel queue.");
         this.close(player);
      } else {
         this.close(player);
         Bukkit.getScheduler().runTask(this.plugin, () -> this.kitSelectGUI.open(player, kit -> {
            String error = this.queueManager.join(player, kit);
            player.sendMessage(error != null ? error : "Joined the ranked duel queue with kit " + kit + ".");
         }));
      }
   }

   private void handleFfaClick(Player player) {
      if (this.ffaManager.isQueued(player.getUniqueId())) {
         String result = this.ffaManager.leave(player.getUniqueId());
         player.sendMessage(result != null ? result : "You're not in the FFA queue.");
         this.close(player);
         return;
      }
      if (!this.ffaManager.available()) {
         player.sendMessage("There's no FFA map set up yet.");
         return;
      }
      this.close(player);
      int forced = this.ffaManager.forcedKit();
      java.util.List<Integer> allowed = this.ffaManager.allowedKits();
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         if (forced > 0) {
            this.joinFfa(player, forced);
         } else if (allowed.isEmpty()) {
            player.sendMessage("No FFA kits are set up yet - ask an admin.");
         } else if (allowed.size() == 1) {
            this.joinFfa(player, allowed.get(0));
         } else {
            this.kitSelectGUI.open(player, allowed, this.ffaManager.kitNames(), kit -> this.joinFfa(player, kit));
         }
      });
   }

   private void joinFfa(Player player, int kit) {
      String error = this.ffaManager.join(player, kit);
      player.sendMessage(error != null ? Component.text(error) : Component.text("Joined the FFA queue" + this.ffaManager.joinSummary(), NamedTextColor.GREEN));
   }

   private void handleChallengeClick(Player player) {      this.close(player);
      Bukkit.getScheduler()
         .runTask(
            this.plugin,
            () -> this.playerPickerGUI
               .open(
                  player,
                  target -> this.kitSelectGUI
                     .open(
                        player,
                        kit -> {
                           String error = this.challengeManager.challenge(player, kit, target);
                           if (error != null) {
                              player.sendMessage(error);
                           } else {
                              player.sendMessage(Component.text("Challenged " + target.getName() + " to a duel.", NamedTextColor.GOLD));
                              long expirySeconds = this.plugin.getConfig().getLong("challenge.expiry-seconds", 60L);
                              target.sendMessage(
                                 Component.text(
                                    player.getName() + " challenged you to a duel! Type /duel to respond (expires in " + expirySeconds + "s).",
                                    NamedTextColor.YELLOW
                                 )
                              );
                           }
                        }
                     )
               )
         );
   }

   private void handleAcceptClick(Player player) {
      if (this.challengeManager.getIncoming(player.getUniqueId()) != null) {
         this.close(player);
         Bukkit.getScheduler().runTask(this.plugin, () -> this.kitSelectGUI.open(player, kit -> {
            String error = this.challengeManager.accept(player, kit);
            if (error != null) {
               player.sendMessage(error);
            }
         }));
      }
   }

   private void handleDenyClick(Player player) {
      boolean had = this.challengeManager.deny(player.getUniqueId());
      player.sendMessage(had ? "Challenge declined." : "You don't have a pending duel challenge.");
      this.close(player);
   }

   private void handleCancelClick(Player player) {
      if (this.challengeManager.cancelOutgoing(player.getUniqueId())) {
         player.sendMessage("Cancelled your outgoing duel challenge.");
      }

      this.close(player);
   }

   private void handleLeaderboardClick(Player player) {
      this.close(player);
      Bukkit.getScheduler().runTask(this.plugin, () -> this.leaderboardGUI.open(player));
   }

   private void handleSeasonClick(Player player) {
      this.close(player);
      this.seasonManager.lastSeasonSummaryLines().forEach(player::sendMessage);
   }
}
