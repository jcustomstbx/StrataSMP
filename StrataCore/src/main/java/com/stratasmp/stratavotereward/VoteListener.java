package com.stratasmp.stratavotereward;

import com.stratasmp.stratacore.StrataModule;
import com.stratasmp.strataeconomy.StrataEconomy;
import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.types.SuffixNode;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class VoteListener implements Listener {
   private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
   private static final Pattern SERVICE_NAME = Pattern.compile("[A-Za-z0-9._ -]{1,64}");

   private final StrataModule plugin;
   private final PendingVotes pendingVotes;
   private final VoteCounts voteCounts;
   private final ProcessedVotes processedVotes;

   public VoteListener(StrataModule plugin, PendingVotes pendingVotes, VoteCounts voteCounts,
                       ProcessedVotes processedVotes) {
      this.plugin = plugin;
      this.pendingVotes = pendingVotes;
      this.voteCounts = voteCounts;
      this.processedVotes = processedVotes;
   }

   @EventHandler
   public void onVote(VotifierEvent event) {
      Vote vote = event.getVote();
      String username = vote.getUsername();
      String service = vote.getServiceName();
      String timestamp = vote.getTimeStamp();
      if (!validVote(username, service, timestamp)) {
         this.plugin.getLogger().warning("Ignoring a vote with an invalid username, service name, or timestamp.");
         return;
      }

      final String voteId;
      try {
         voteId = voteId(service, username, timestamp);
      } catch (NoSuchAlgorithmException impossible) {
         this.plugin.getLogger().severe("SHA-256 is unavailable; refusing to process a vote.");
         return;
      }

      ProcessedVotes.BeginResult result = this.processedVotes.begin(voteId);
      if (result == ProcessedVotes.BeginResult.DUPLICATE) {
         this.plugin.getLogger().info("Ignoring a repeated vote receipt.");
         return;
      }
      if (result == ProcessedVotes.BeginResult.UNAVAILABLE) {
         this.plugin.getLogger().severe("Vote receipt storage is unavailable; refusing to issue a payout.");
         return;
      }

      try {
         Bukkit.getScheduler().runTask(this.plugin,
               () -> processVote(voteId, username, service, timestamp));
      } catch (RuntimeException schedulingFailure) {
         this.processedVotes.release(voteId);
         this.plugin.getLogger().warning("Could not schedule a vote payout: " + schedulingFailure.getMessage());
      }
   }

   private void processVote(String voteId, String username, String service, String timestamp) {
      int amount = this.plugin.getConfig().getInt("reward-amount", 250);
      if (amount < 1 || amount > 1_000_000) {
         this.processedVotes.release(voteId);
         this.plugin.getLogger().severe("Vote reward-amount must be between 1 and 1000000; vote was not processed.");
         return;
      }

      StrataEconomy stratas = this.plugin.core().module(StrataEconomy.class);
      if (stratas == null || stratas.stratas() == null) {
         this.processedVotes.release(voteId);
         this.plugin.getLogger().severe("StrataEconomy is unavailable; vote was not processed.");
         return;
      }

      try {
         this.processedVotes.commit(voteId);
      } catch (Exception failure) {
         this.plugin.getLogger().severe("Could not persist a vote receipt; payout skipped: " + failure.getMessage());
         return;
      }

      Player online = findOnline(username);
      if (online != null) {
         pay(stratas, online.getUniqueId(), online.getName(), amount);
         giveVoteKey(online.getName());
         countVotes(online.getUniqueId(), online.getName(), 1);
      } else {
         // cached lookup only: the by-name version can block the tick on a Mojang request
         OfflinePlayer known = Bukkit.getOfflinePlayerIfCached(username);
         if (known == null || !known.hasPlayedBefore()) {
            this.pendingVotes.add(username, amount);
            int keys = voteKeyAmount();
            if (keys > 0) {
               this.pendingVotes.addKeys(username, keys);
            }
            this.plugin.getLogger().info("Vote from '" + username
                  + "' queued for their first join via " + service + ".");
         } else {
            pay(stratas, known.getUniqueId(), username, amount);
            giveVoteKey(username);
            countVotes(known.getUniqueId(), username, 1);
         }
      }
      this.plugin.getLogger().info("Processed vote receipt from " + username + " via " + service
            + " (" + timestamp + ").");
   }

   private void pay(StrataEconomy stratas, UUID playerId, String username, int amount) {
      stratas.stratas().deposit(playerId, amount);
      if (this.plugin.getConfig().getBoolean("broadcast", true)) {
         String message = this.plugin.getConfig().getString("broadcast-message", "")
               .replace("%player%", username)
               .replace("%amount%", String.valueOf(amount));
         if (!message.isEmpty()) {
            Bukkit.broadcast(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
         }
      }
   }

   private Player findOnline(String username) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         String name = player.getName();
         if (name.equalsIgnoreCase(username) || name.equalsIgnoreCase("." + username)) {
            return player;
         }
      }
      return null;
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      String bare = player.getName().startsWith(".") ? player.getName().substring(1) : player.getName();
      int pending = this.pendingVotes.takePending(player.getName())
            + (bare.equals(player.getName()) ? 0 : this.pendingVotes.takePending(bare));
      if (pending > 0) {
         StrataEconomy stratas = this.plugin.core().module(StrataEconomy.class);
         if (stratas == null || stratas.stratas() == null) {
            this.plugin.getLogger().severe("StrataEconomy is unavailable; queued vote stratas were not paid.");
         } else {
            this.plugin.getLogger().info("Paying out " + pending + " queued stratas to " + player.getName() + ".");
            pay(stratas, player.getUniqueId(), player.getName(), pending);
            int perVote = Math.max(1, this.plugin.getConfig().getInt("reward-amount", 250));
            countVotes(player.getUniqueId(), player.getName(), Math.max(1, pending / perVote));
         }
      }

      int pendingKeys = this.pendingVotes.takePendingKeys(player.getName())
            + (bare.equals(player.getName()) ? 0 : this.pendingVotes.takePendingKeys(bare));
      if (pendingKeys > 0) {
         this.plugin.getLogger().info("Paying out " + pendingKeys + " queued vote key(s) to " + player.getName() + ".");
         giveVoteKey(player.getName(), pendingKeys);
      }
   }

   private void countVotes(UUID id, String name, int votes) {
      int before = this.voteCounts.get(id);
      int total = this.voteCounts.add(id, votes);
      int needed = this.plugin.getConfig().getInt("voter-tag.votes", 50);
      if (!this.plugin.getConfig().getBoolean("voter-tag.enabled", true) || before >= needed || total < needed) {
         return;
      }

      String message = this.plugin.getConfig()
            .getString("voter-tag.broadcast-message", "&e%player% &fhas voted &6%votes% times &fand earned the &a&l[VOTER] &ftag!")
            .replace("%player%", name)
            .replace("%votes%", String.valueOf(total));
      SuffixNode voterSuffix = SuffixNode.builder("&a&l[VOTER]", 5).build();
      try {
         LuckPermsProvider.get().getUserManager().modifyUser(id, user -> user.data().add(voterSuffix))
               .whenComplete((ignored, failure) -> {
                  if (failure != null) {
                     this.plugin.getLogger().warning("Could not grant the voter suffix to " + id + ": "
                           + failure.getMessage());
                     return;
                  }
                  this.plugin.getLogger().info(name + " reached " + total + " votes - granted the voter tag.");
                  if (!message.isEmpty()) {
                     Bukkit.getScheduler().runTask(this.plugin, () -> Bukkit.broadcast(
                           LegacyComponentSerializer.legacyAmpersand().deserialize(message)));
                  }
               });
      } catch (IllegalStateException unavailable) {
         this.plugin.getLogger().warning("LuckPerms is unavailable; could not grant the voter suffix to " + id + ".");
      }
   }

   private void giveVoteKey(String username) {
      giveVoteKey(username, voteKeyAmount());
   }

   private void giveVoteKey(String username, int amount) {
      if (amount <= 0 || !this.plugin.getConfig().getBoolean("vote-key.enabled", false)) {
         return;
      }
      String crate = this.plugin.getConfig().getString("vote-key.crate", "");
      String type = this.plugin.getConfig().getString("vote-key.type", "virtual");
      if (!PLAYER_NAME.matcher(username).matches()
            || !crate.matches("[A-Za-z0-9_-]{1,32}")
            || !(type.equalsIgnoreCase("virtual") || type.equalsIgnoreCase("physical"))) {
         this.plugin.getLogger().warning("Vote key settings or target name are invalid; no key was issued.");
         return;
      }
      boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
            "crates give " + type.toLowerCase(Locale.ROOT) + " " + crate + " " + amount + " " + username + " -s");
      if (!dispatched) {
         this.plugin.getLogger().warning("CrazyCrates did not accept the vote-key command for " + username + ".");
      }
   }

   private int voteKeyAmount() {
      if (!this.plugin.getConfig().getBoolean("vote-key.enabled", false)) {
         return 0;
      }
      int amount = this.plugin.getConfig().getInt("vote-key.amount", 1);
      if (amount < 1 || amount > 64) {
         this.plugin.getLogger().warning("vote-key.amount must be between 1 and 64; no vote keys will be issued.");
         return 0;
      }
      return amount;
   }

   private static boolean validVote(String username, String service, String timestamp) {
      return username != null && PLAYER_NAME.matcher(username).matches()
            && service != null && SERVICE_NAME.matcher(service).matches()
            && timestamp != null && !timestamp.isBlank() && timestamp.length() <= 64
            && timestamp.chars().noneMatch(Character::isISOControl);
   }

   private static String voteId(String service, String username, String timestamp) throws NoSuchAlgorithmException {
      String canonical = service.toLowerCase(Locale.ROOT) + "\n"
            + username.toLowerCase(Locale.ROOT) + "\n" + timestamp;
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
   }
}
