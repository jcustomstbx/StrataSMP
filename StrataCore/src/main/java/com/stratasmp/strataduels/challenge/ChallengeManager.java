package com.stratasmp.strataduels.challenge;

import com.stratasmp.strataduels.match.MatchManager;
import com.stratasmp.strataduels.queue.QueueManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import com.stratasmp.stratacore.StrataModule;

public class ChallengeManager {
   private final StrataModule plugin;
   private final MatchManager matchManager;
   private final QueueManager queueManager;
   private final Map<UUID, PendingChallenge> byTarget = new HashMap<>();

   public ChallengeManager(StrataModule plugin, MatchManager matchManager, QueueManager queueManager) {
      this.plugin = plugin;
      this.matchManager = matchManager;
      this.queueManager = queueManager;
   }

   public String challenge(Player challenger, int challengerKit, Player target) {
      UUID challengerUuid = challenger.getUniqueId();
      UUID targetUuid = target.getUniqueId();
      if (challengerUuid.equals(targetUuid)) {
         return "You can't duel yourself.";
      } else if (this.matchManager.isBusy(challengerUuid) || this.queueManager.isQueued(challengerUuid)) {
         return "You're already in a duel or queue.";
      } else if (this.matchManager.isBusy(targetUuid)) {
         return target.getName() + " is already in a duel.";
      } else if (this.byTarget.containsKey(targetUuid)) {
         return target.getName() + " already has a pending challenge.";
      } else {
         long expirySeconds = this.plugin.getConfig().getLong("challenge.expiry-seconds", 60L);
         long expiresAt = System.currentTimeMillis() + expirySeconds * 1000L;
         this.byTarget.put(targetUuid, new PendingChallenge(challengerUuid, challengerKit, targetUuid, expiresAt));
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.expireIfStillPending(targetUuid), expirySeconds * 20L);
         return null;
      }
   }

   public String accept(Player target, int targetKit) {
      UUID targetUuid = target.getUniqueId();
      PendingChallenge challenge = this.byTarget.get(targetUuid);
      if (challenge != null && challenge.expiresAtMillis() >= System.currentTimeMillis()) {
         this.byTarget.remove(targetUuid);
         if (!this.matchManager.isBusy(targetUuid) && !this.queueManager.isQueued(targetUuid)) {
            return this.matchManager.isBusy(challenge.challenger())
               ? "That challenger is no longer available."
               : this.matchManager.startMatch(challenge.challenger(), challenge.challengerKit(), targetUuid, targetKit);
         } else {
            return "You're already in a duel or queue.";
         }
      } else {
         this.byTarget.remove(targetUuid);
         return "You don't have a pending duel challenge.";
      }
   }

   public boolean deny(UUID targetUuid) {
      PendingChallenge removed = this.byTarget.remove(targetUuid);
      if (removed == null) {
         return false;
      } else {
         this.notifyIfOnline(removed.challenger(), Component.text("Your duel challenge was declined.", NamedTextColor.RED));
         return true;
      }
   }

   public boolean cancelOutgoing(UUID challengerUuid) {
      UUID targetToRemove = null;

      for (Entry<UUID, PendingChallenge> entry : this.byTarget.entrySet()) {
         if (entry.getValue().challenger().equals(challengerUuid)) {
            targetToRemove = entry.getKey();
            break;
         }
      }

      if (targetToRemove == null) {
         return false;
      } else {
         this.byTarget.remove(targetToRemove);
         this.notifyIfOnline(targetToRemove, Component.text("The duel challenge was cancelled.", NamedTextColor.RED));
         return true;
      }
   }

   public boolean hasPendingChallenge(UUID targetUuid) {
      return this.byTarget.containsKey(targetUuid);
   }

   public PendingChallenge getIncoming(UUID targetUuid) {
      PendingChallenge challenge = this.byTarget.get(targetUuid);
      return challenge != null && challenge.expiresAtMillis() >= System.currentTimeMillis() ? challenge : null;
   }

   public PendingChallenge getOutgoing(UUID challengerUuid) {
      for (PendingChallenge challenge : this.byTarget.values()) {
         if (challenge.challenger().equals(challengerUuid) && challenge.expiresAtMillis() >= System.currentTimeMillis()) {
            return challenge;
         }
      }

      return null;
   }

   private void expireIfStillPending(UUID targetUuid) {
      PendingChallenge challenge = this.byTarget.get(targetUuid);
      if (challenge != null && challenge.expiresAtMillis() <= System.currentTimeMillis()) {
         this.byTarget.remove(targetUuid);
         this.notifyIfOnline(targetUuid, Component.text("A duel challenge expired.", NamedTextColor.GRAY));
         this.notifyIfOnline(challenge.challenger(), Component.text("Your duel challenge expired.", NamedTextColor.GRAY));
      }
   }

   private void notifyIfOnline(UUID uuid, Component message) {
      Player player = Bukkit.getPlayer(uuid);
      if (player != null) {
         player.sendMessage(message);
      }
   }
}
