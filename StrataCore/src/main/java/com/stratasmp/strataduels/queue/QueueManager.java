package com.stratasmp.strataduels.queue;

import com.stratasmp.strataduels.DataManager;
import com.stratasmp.strataduels.DuelPlayerData;
import com.stratasmp.strataduels.match.MatchManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.scheduler.BukkitTask;

public class QueueManager {
   private final StrataModule plugin;
   private final MatchManager matchManager;
   private final DataManager dataManager;
   private final Map<UUID, QueueManager.QueueEntry> queued = new LinkedHashMap<>();
   private BukkitTask task;

   public QueueManager(StrataModule plugin, MatchManager matchManager, DataManager dataManager) {
      this.plugin = plugin;
      this.matchManager = matchManager;
      this.dataManager = dataManager;
   }

   public void start() {
      int intervalSeconds = this.plugin.getConfig().getInt("queue.check-interval-seconds", 2);
      this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tryPairings, 20L, intervalSeconds * 20L);
   }

   public void stop() {
      if (this.task != null) {
         this.task.cancel();
      }
      this.queued.clear();
   }

   public boolean isQueued(UUID uuid) {
      return this.queued.containsKey(uuid);
   }

   public String join(Player player, int kit) {
      UUID uuid = player.getUniqueId();
      if (this.matchManager.isBusy(uuid)) {
         return "You're already in a duel.";
      } else if (this.queued.containsKey(uuid)) {
         return "You're already queued.";
      } else {
         DuelPlayerData data = this.dataManager.get(uuid);
         double elo = data != null ? data.seasonElo : 1000.0;
         this.queued.put(uuid, new QueueManager.QueueEntry(uuid, kit, elo, System.currentTimeMillis()));
         // don't make someone wait out the rest of the periodic check-interval if
         // a match is already sitting there waiting for them - try right away,
         // one tick later so the caller's own "joined the queue" message lands first
         Bukkit.getScheduler().runTask(this.plugin, this::tryPairings);
         return null;
      }
   }

   public boolean leave(UUID uuid) {
      return this.queued.remove(uuid) != null;
   }

   /** Called by MatchManager the instant an arena frees up, so anyone stuck on "no arena free" doesn't wait out the poll timer. */
   public void checkForMatches() {
      this.tryPairings();
   }

   private void tryPairings() {
      // someone who got into a match another way (a challenge, say) is no longer waiting
      this.queued.keySet().removeIf(this.matchManager::isBusy);
      if (this.queued.size() >= 2) {
         int initialRange = this.plugin.getConfig().getInt("queue.initial-range", 100);
         double widenPerSecond = this.plugin.getConfig().getDouble("queue.widen-per-second", 5.0);
         List<QueueManager.QueueEntry> candidates = new ArrayList<>(this.queued.values());
         List<UUID> matchedThisPass = new ArrayList<>();

         for (int i = 0; i < candidates.size(); i++) {
            QueueManager.QueueEntry entry = candidates.get(i);
            if (!matchedThisPass.contains(entry.uuid())) {
               double secondsWaited = (System.currentTimeMillis() - entry.joinedAtMillis()) / 1000.0;
               double allowedRange = initialRange + widenPerSecond * secondsWaited;
               QueueManager.QueueEntry closest = null;
               double closestDiff = Double.MAX_VALUE;

               for (int j = 0; j < candidates.size(); j++) {
                  if (i != j) {
                     QueueManager.QueueEntry other = candidates.get(j);
                     if (!matchedThisPass.contains(other.uuid())) {
                        double diff = Math.abs(entry.elo() - other.elo());
                        if (diff <= allowedRange && diff < closestDiff) {
                           closest = other;
                           closestDiff = diff;
                        }
                     }
                  }
               }

               if (closest != null) {
                  this.queued.remove(entry.uuid());
                  this.queued.remove(closest.uuid());
                  String error = this.matchManager.startMatch(entry.uuid(), entry.kit(), closest.uuid(), closest.kit());
                  if (error != null) {
                     this.queued.put(entry.uuid(), entry);
                     this.queued.put(closest.uuid(), closest);
                     this.notifyNoArena(entry.uuid());
                  } else {
                     matchedThisPass.add(entry.uuid());
                     matchedThisPass.add(closest.uuid());
                  }
               }
            }
         }
      }
   }

   private void notifyNoArena(UUID uuid) {
      Player player = Bukkit.getPlayer(uuid);
      if (player != null) {
         player.sendMessage(Component.text("Found a match, but no arena is free yet - waiting...", NamedTextColor.YELLOW));
      }
   }

   private record QueueEntry(UUID uuid, int kit, double elo, long joinedAtMillis) {
   }
}
