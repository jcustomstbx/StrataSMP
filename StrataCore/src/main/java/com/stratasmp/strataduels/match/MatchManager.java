package com.stratasmp.strataduels.match;

import com.stratasmp.strataduels.DataManager;
import com.stratasmp.strataduels.DuelPlayerData;
import com.stratasmp.strataduels.EloCalculator;
import com.stratasmp.strataduels.arena.Arena;
import com.stratasmp.strataduels.arena.ArenaManager;
import com.stratasmp.strataduels.arena.ArenaResetManager;
import com.stratasmp.strataduels.kit.KitManager;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.scheduler.BukkitRunnable;

public class MatchManager {
   private final StrataModule plugin;
   private final ArenaManager arenaManager;
   private final ArenaResetManager arenaResetManager;
   private final KitManager kitManager;
   private final DataManager dataManager;
   private final PendingRestores pendingRestores;
   private Runnable onArenaFreed = () -> {
   };
   private Predicate<UUID> externalBusy = uuid -> false;
   private final Map<UUID, DuelMatch> activeByPlayer = new HashMap<>();
   private final Set<UUID> frozen = new HashSet<>();
   private static final Color[] VICTORY_FIREWORK_COLORS = new Color[]{Color.YELLOW, Color.ORANGE, Color.WHITE, Color.RED, Color.LIME};
   private static final Type[] VICTORY_FIREWORK_TYPES = new Type[]{Type.BALL_LARGE, Type.BURST, Type.STAR};
   public static final String DUEL_TAG_KEY = "strataduels-active";

   public MatchManager(StrataModule plugin, ArenaManager arenaManager, ArenaResetManager arenaResetManager, KitManager kitManager, DataManager dataManager) {
      this.plugin = plugin;
      this.arenaManager = arenaManager;
      this.arenaResetManager = arenaResetManager;
      this.kitManager = kitManager;
      this.dataManager = dataManager;
      this.pendingRestores = new PendingRestores(plugin);
   }

   /** Lets QueueManager retry pairings the instant an arena frees up, instead of waiting for its own poll timer. */
   public void setOnArenaFreed(Runnable onArenaFreed) {
      this.onArenaFreed = onArenaFreed;
   }

   /**
    * Call from the join listener - restores whatever a player was still holding a duel kit for
    * when they last disconnected (a forfeit-by-disconnect), and sends them back to the SMP
    * specifically rather than wherever the server's normal join logic would otherwise land them
    * (the hub, per Multiverse's join-destination) - they abandoned a match, so put them back in
    * survival rather than a lobby they might have queued from.
    */
   public void applyPendingRestoreIfAny(Player player) {
      PlayerSnapshot snapshot = this.pendingRestores.takeIfPresent(player.getUniqueId());
      if (snapshot != null) {
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline() && !player.isDead()) {
               snapshot.applyState(player);
               this.teleportToSmp(player);
               player.sendMessage(Component.text("Your inventory from before an interrupted duel has been restored.", NamedTextColor.YELLOW));
            } else {
               // left again or died before it could be applied: keep it for the next join or respawn
               this.pendingRestores.saveQuietly(player.getUniqueId(), snapshot);
            }
         });
      }
   }

   private void teleportToSmp(Player player) {
      String smpWorldName = this.plugin.getConfig().getString("restore-disconnect-world", "world");
      World smpWorld = Bukkit.getWorld(smpWorldName);
      if (smpWorld != null) {
         player.teleport(smpWorld.getSpawnLocation());
      }
   }

   /** Anything else that keeps a player out of duels (the FFA queue and rounds) - checked wherever a duel needs a free player. */
   public void setExternalBusy(Predicate<UUID> externalBusy) {
      this.externalBusy = externalBusy;
   }

   public boolean isInMatch(UUID uuid) {
      return this.activeByPlayer.containsKey(uuid);
   }

   public boolean isBusy(UUID uuid) {
      return this.activeByPlayer.containsKey(uuid) || this.externalBusy.test(uuid);
   }

   public void forceResolveAllActive() {
      for (DuelMatch match : new HashSet<>(this.activeByPlayer.values())) {
         try {
            // a match that was already decided and only waiting out its end delay keeps its result
            if (match.state == DuelMatch.State.ENDING && match.decidedOutcome != null) {
               this.resolveMatch(match, match.decidedOutcome, match.decidedWinner, match.decidedLoser);
            } else {
               this.resolveMatch(match, MatchManager.Outcome.DRAW, null, null);
            }
         } catch (RuntimeException e) {
            this.plugin.getLogger().warning("Couldn't cleanly end a duel during shutdown: " + e);
         }
      }
   }

   public DuelMatch getActiveMatchFor(UUID uuid) {
      return this.activeByPlayer.get(uuid);
   }

   public boolean isFrozen(UUID uuid) {
      return this.frozen.contains(uuid);
   }

   public String startMatch(UUID uuidA, int kitA, UUID uuidB, int kitB) {
      Player playerA = Bukkit.getPlayer(uuidA);
      Player playerB = Bukkit.getPlayer(uuidB);
      if (uuidA.equals(uuidB) || this.isBusy(uuidA) || this.isBusy(uuidB)) {
         return "One of the duelists is already in a match.";
      }
      if (playerA != null && playerB != null) {
         Optional<Arena> maybeArena = this.arenaManager.findFreeArena();
         if (maybeArena.isEmpty()) {
            return "No duel arena is free right now - try again shortly.";
         } else {
            Arena arena = maybeArena.get();
            this.arenaManager.markInUse(arena, true);
            DuelMatch match = new DuelMatch(uuidA, uuidB, kitA, kitB, arena);
            this.activeByPlayer.put(uuidA, match);
            this.activeByPlayer.put(uuidB, match);
            // close any open window first (a trade GUI hands its items back on close) so they land in the snapshot
            playerA.closeInventory();
            playerB.closeInventory();
            match.snapshotA = PlayerSnapshot.capture(playerA);
            match.snapshotB = PlayerSnapshot.capture(playerB);
            this.pendingRestores.saveQuietly(uuidA, match.snapshotA);
            this.pendingRestores.saveQuietly(uuidB, match.snapshotB);
            playerA.teleport(arena.location1());
            playerB.teleport(arena.location2());
            Bukkit.getScheduler().runTask(this.plugin, () -> {
               this.healToFullNoAdjustments(playerA);
               this.healToFullNoAdjustments(playerB);
               this.kitManager.applyKit(kitA, playerA);
               this.kitManager.applyKit(kitB, playerB);
               playerA.updateInventory();
               playerB.updateInventory();
            });
            match.state = DuelMatch.State.COUNTDOWN;
            this.frozen.add(uuidA);
            this.frozen.add(uuidB);
            this.broadcastToMatch(match, Component.text("Duel starting at " + arena.name + "!", NamedTextColor.GOLD));
            int countdownSeconds = this.plugin.getConfig().getInt("match.countdown-seconds", 5);
            this.startCountdown(match, countdownSeconds);
            return null;
         }
      } else {
         return "One of the duelists went offline.";
      }
   }

   /**
    * A solo test session: the caller is teleported into the named arena and treated exactly like an
    * active duelist - kit, the command blacklist, the barrier/boundary checks, all of it - so an admin
    * can throw pearls at the walls without needing a second player. No opponent, no countdown, no
    * timeout, no effect on ELO or the leaderboard; it only ends when {@link #endTest} is called.
    * {@code playerA.equals(playerB)} is what marks a match as a test rather than a real duel.
    */
   public String startTest(Player player, Arena arena, int kit) {
      if (this.isBusy(player.getUniqueId())) {
         return "You're already in a duel or a test - end it first with /duel end.";
      }
      if (!arena.isReady()) {
         return "That arena isn't fully set up (needs pos1, pos2 and a loaded world).";
      }
      if (arena.inUse) {
         return "That arena is in use by a real match right now.";
      }
      UUID uuid = player.getUniqueId();
      this.arenaManager.markInUse(arena, true);
      DuelMatch match = new DuelMatch(uuid, uuid, kit, kit, arena);
      match.state = DuelMatch.State.ACTIVE;
      match.activeSince = System.currentTimeMillis();
      player.closeInventory();
      match.snapshotA = PlayerSnapshot.capture(player);
      this.pendingRestores.saveQuietly(uuid, match.snapshotA);
      this.activeByPlayer.put(uuid, match);
      player.teleport(arena.location1());
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         this.healToFullNoAdjustments(player);
         this.kitManager.applyKit(kit, player);
         player.updateInventory();
      });
      return null;
   }

   /** Ends a test session started with {@link #startTest} - not a normal match, so this skips resolveMatch entirely. */
   public String endTest(Player player) {
      UUID uuid = player.getUniqueId();
      DuelMatch match = this.activeByPlayer.get(uuid);
      if (match == null || !match.playerA.equals(match.playerB)) {
         return "You're not in a test duel.";
      }
      // a lethal hit may have queued a delayed resolve; mark the match finished so that never runs afterwards
      match.state = DuelMatch.State.COMPLETE;
      if (match.countdownTask != null) match.countdownTask.cancel();
      if (match.timeoutTask != null) match.timeoutTask.cancel();
      this.activeByPlayer.remove(uuid);
      this.frozen.remove(uuid);
      if (match.snapshotA != null) {
         player.teleport(match.snapshotA.location());
         match.snapshotA.applyState(player);
         this.pendingRestores.discard(uuid);
      }
      // the arena only frees up once the reset has finished
      this.arenaResetManager.resetArena(match.arena, () -> {
         this.arenaManager.markInUse(match.arena, false);
         this.onArenaFreed.run();
      });
      return null;
   }

   private void healToFullNoAdjustments(Player player) {
      AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
      double max = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
      player.setHealth(max);
   }

   private void startCountdown(DuelMatch match, int secondsLeft) {
      if (match.state == DuelMatch.State.COUNTDOWN) {
         if (secondsLeft <= 0) {
            this.beginActive(match);
         } else {
            this.broadcastToMatch(match, Component.text(secondsLeft + "...", NamedTextColor.YELLOW));
            match.countdownTask = Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.startCountdown(match, secondsLeft - 1), 20L);
         }
      }
   }

   private void beginActive(DuelMatch match) {
      match.state = DuelMatch.State.ACTIVE;
      match.activeSince = System.currentTimeMillis();
      this.frozen.remove(match.playerA);
      this.frozen.remove(match.playerB);
      this.broadcastToMatch(match, Component.text("FIGHT!", NamedTextColor.RED));
      this.setDuelTag(match.playerA);
      this.setDuelTag(match.playerB);
      int maxDuration = this.plugin.getConfig().getInt("match.max-duration-seconds", 300);
      match.timeoutTask = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         if (match.state == DuelMatch.State.ACTIVE) {
            this.broadcastToMatch(match, Component.text("Time's up - the duel ended in a draw.", NamedTextColor.GRAY));
            this.endMatch(match, MatchManager.Outcome.DRAW, null, null);
         }
      }, maxDuration * 20L);
   }

   public void handleLethalHit(UUID victim) {
      DuelMatch match = this.activeByPlayer.get(victim);
      if (match != null && match.state == DuelMatch.State.ACTIVE) {
         UUID winner = match.opponentOf(victim);
         this.playVictoryAnimation(winner, victim);
         this.endMatch(match, MatchManager.Outcome.WIN, winner, victim);
      }
   }

   private void endMatch(DuelMatch match, MatchManager.Outcome outcome, UUID winnerUuid, UUID loserUuid) {
      if (match.state != DuelMatch.State.ENDING && match.state != DuelMatch.State.COMPLETE) {
         match.state = DuelMatch.State.ENDING;
         match.decidedOutcome = outcome;
         match.decidedWinner = winnerUuid;
         match.decidedLoser = loserUuid;
         if (match.timeoutTask != null) {
            match.timeoutTask.cancel();
         }

         this.frozen.add(match.playerA);
         this.frozen.add(match.playerB);
         int endDelaySeconds = this.plugin.getConfig().getInt("match.end-delay-seconds", 5);
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.resolveMatch(match, outcome, winnerUuid, loserUuid), endDelaySeconds * 20L);
      }
   }

   private void playVictoryAnimation(UUID winnerUuid, UUID loserUuid) {
      final Player winner = Bukkit.getPlayer(winnerUuid);
      Player loser = Bukkit.getPlayer(loserUuid);
      if (winner != null) {
         winner.showTitle(
            Title.title(
               Component.text("VICTORY!", NamedTextColor.GOLD),
               Component.text(this.nameOf(loserUuid) + " has been defeated", NamedTextColor.YELLOW),
               Times.times(Duration.ofMillis(200L), Duration.ofSeconds(2L), Duration.ofMillis(500L))
            )
         );
         int endDelaySeconds = this.plugin.getConfig().getInt("match.end-delay-seconds", 5);
         final int bursts = Math.max(4, endDelaySeconds * 2);
         (new BukkitRunnable() {
            int fired = 0;

            public void run() {
               if (this.fired < bursts && winner.isOnline()) {
                  MatchManager.this.spawnVictoryFirework(winner);
                  this.fired++;
               } else {
                  this.cancel();
               }
            }
         }).runTaskTimer(this.plugin, 0L, 8L);
      }

      if (loser != null) {
         loser.showTitle(
            Title.title(
               Component.text("DEFEAT", NamedTextColor.RED),
               Component.text("Better luck next time", NamedTextColor.GRAY),
               Times.times(Duration.ofMillis(200L), Duration.ofSeconds(2L), Duration.ofMillis(500L))
            )
         );
      }
   }

   private void spawnVictoryFirework(Player winner) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      Location loc = winner.getLocation().add(random.nextDouble(-1.5, 1.5), 0.2, random.nextDouble(-1.5, 1.5));
      Firework firework = (Firework)winner.getWorld().spawn(loc, Firework.class);
      FireworkMeta meta = firework.getFireworkMeta();
      meta.addEffect(
         FireworkEffect.builder()
            .withColor(VICTORY_FIREWORK_COLORS[random.nextInt(VICTORY_FIREWORK_COLORS.length)])
            .withFade(Color.WHITE)
            .with(VICTORY_FIREWORK_TYPES[random.nextInt(VICTORY_FIREWORK_TYPES.length)])
            .trail(random.nextBoolean())
            .flicker(true)
            .build()
      );
      meta.setPower(1);
      firework.setFireworkMeta(meta);
      winner.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2F, 1.0F);
      winner.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0F, 1.0F);
   }

   public void forfeit(UUID quitter) {
      DuelMatch match = this.activeByPlayer.get(quitter);
      if (match != null) {
         if (match.state == DuelMatch.State.ENDING && match.decidedOutcome != null) {
            // already decided: the quitter leaving during the end delay must not change who won
            this.resolveMatch(match, match.decidedOutcome, match.decidedWinner, match.decidedLoser);
            return;
         }
         UUID winner = match.opponentOf(quitter);
         this.resolveMatch(match, MatchManager.Outcome.FORFEIT, winner, quitter);
      }
   }

   private void resolveMatch(DuelMatch match, MatchManager.Outcome outcome, UUID winnerUuid, UUID loserUuid) {
      if (match.state != DuelMatch.State.COMPLETE) {
         match.state = DuelMatch.State.COMPLETE;
         if (match.countdownTask != null) {
            match.countdownTask.cancel();
         }

         if (match.timeoutTask != null) {
            match.timeoutTask.cancel();
         }

         this.frozen.remove(match.playerA);
         this.frozen.remove(match.playerB);
         this.activeByPlayer.remove(match.playerA);
         this.activeByPlayer.remove(match.playerB);
         this.clearDuelTag(match.playerA);
         this.clearDuelTag(match.playerB);
         this.clearCombatTags(match.playerA);
         this.clearCombatTags(match.playerB);
         this.restoreIfOnline(match.playerA, match.snapshotA);
         this.restoreIfOnline(match.playerB, match.snapshotB);
         // only make the arena available to a new match once it's actually been restored -
         // both duelists are already teleported out above, so nobody's standing in it while
         // this runs, but freeing it earlier let a new pair get matched into an arena whose
         // previous damage was still mid-restore underneath them.
         this.arenaResetManager.resetArena(match.arena, () -> {
            this.arenaManager.markInUse(match.arena, false);
            this.onArenaFreed.run();
         });
         // a solo admin test (A == B) never counts towards stats or ELO
         if (!match.playerA.equals(match.playerB)) switch (outcome) {
            case WIN:
            case FORFEIT:
               this.applyMatchResult(winnerUuid, loserUuid);
               String suffix = outcome == MatchManager.Outcome.FORFEIT ? " (forfeit)" : "";
               this.broadcastToMatch(
                  match, Component.text(this.nameOf(winnerUuid) + " defeated " + this.nameOf(loserUuid) + suffix + "!", NamedTextColor.GREEN)
               );
               break;
            case DRAW:
               this.applyDrawStats(match.playerA);
               this.applyDrawStats(match.playerB);
               this.broadcastToMatch(match, Component.text("The duel ended in a draw - no rating change.", NamedTextColor.GRAY));
         }
      }
   }

   private void restoreIfOnline(UUID uuid, PlayerSnapshot snapshot) {
      if (snapshot == null) {
         return;
      }
      Player player = Bukkit.getPlayer(uuid);
      if (player == null || player.isDead()) {
         // already disconnected by the time the match resolved (e.g. this IS the quit event that triggered the
         // forfeit), or on the death screen: queue the restore for their next join or respawn instead of
         // applying it to a player who is about to be replaced by a fresh entity.
         this.pendingRestores.saveQuietly(uuid, snapshot);
         return;
      }
      player.teleport(snapshot.location());
      if (!this.plugin.isEnabled()) {
         if (player.isOnline() && !player.isDead()) {
            snapshot.applyState(player);
            this.pendingRestores.discard(uuid);
         } else {
            this.pendingRestores.saveQuietly(uuid, snapshot);
         }
      } else {
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline() && !player.isDead()) {
               snapshot.applyState(player);
               this.pendingRestores.discard(uuid);
            } else {
               this.pendingRestores.saveQuietly(uuid, snapshot);
            }
         });
      }
   }

   private void setDuelTag(UUID uuid) {
      Player player = Bukkit.getPlayer(uuid);
      if (player != null) {
         player.setMetadata("strataduels-active", new FixedMetadataValue(this.plugin, true));
      }
   }

   private void clearDuelTag(UUID uuid) {
      Player player = Bukkit.getPlayer(uuid);
      if (player != null) {
         player.removeMetadata("strataduels-active", this.plugin);
      }
   }

   /** Clears every other plugin's "in combat" state for a duelist - a duel has its own rules and must never leave a timer behind. */
   public void clearCombatTags(UUID uuid) {
      this.clearTeamsCombatTag(uuid);
      this.clearRankEssentialsCombatTag(uuid);
   }

   /**
    * RankEssentials tags both players for 30s on any player-vs-player hit and knows nothing about
    * duels, so without this every duel left a combat timer running afterwards (blocking commands,
    * and dropping the player's whole inventory if they logged out inside it).
    */
   private void clearRankEssentialsCombatTag(UUID uuid) {
      try {
         Plugin rankEssentials = Bukkit.getPluginManager().getPlugin("RankEssentials");
         if (rankEssentials == null) {
            return;
         }

         Object combatManager = rankEssentials.getClass().getMethod("getCombatManager").invoke(rankEssentials);
         if (combatManager == null) {
            return;
         }

         combatManager.getClass().getMethod("untag", UUID.class).invoke(combatManager, uuid);
      } catch (Throwable ignored) {
      }
   }

   private void clearTeamsCombatTag(UUID uuid) {
      try {
         Plugin strataTeams = this.plugin.core().module(com.stratasmp.stratateams.StrataTeams.class);
         if (strataTeams == null) {
            return;
         }

         Object combatTracker = strataTeams.getClass().getMethod("getCombatTracker").invoke(strataTeams);
         if (combatTracker == null) {
            return;
         }

         combatTracker.getClass().getMethod("clearCombat", UUID.class).invoke(combatTracker, uuid);
      } catch (Throwable var4) {
      }
   }

   private void applyDrawStats(UUID uuid) {
      DuelPlayerData data = this.dataManager.get(uuid);
      if (data != null) {
         data.seasonGamesPlayed++;
      }
   }

   private void applyMatchResult(UUID winnerUuid, UUID loserUuid) {
      DuelPlayerData winner = this.dataManager.get(winnerUuid);
      DuelPlayerData loser = this.dataManager.get(loserUuid);
      if (winner != null && loser != null) {
         int placementGames = this.plugin.getConfig().getInt("elo.placement-games", 10);
         int placementK = this.plugin.getConfig().getInt("elo.k-factor-placement", 40);
         int standardK = this.plugin.getConfig().getInt("elo.k-factor-standard", 20);
         double floor = this.plugin.getConfig().getDouble("elo.floor", 100.0);
         EloCalculator.MatchResult result = EloCalculator.applyMatch(
            winner.seasonElo, winner.seasonGamesPlayed, loser.seasonElo, loser.seasonGamesPlayed, placementGames, placementK, standardK, floor
         );
         winner.seasonElo = result.winnerNewElo();
         winner.seasonWins++;
         winner.seasonGamesPlayed++;
         winner.seasonWinStreak++;
         winner.seasonBestStreak = Math.max(winner.seasonBestStreak, winner.seasonWinStreak);
         winner.lifetimeWins++;
         winner.lifetimeGamesPlayed++;
         loser.seasonElo = result.loserNewElo();
         loser.seasonLosses++;
         loser.seasonGamesPlayed++;
         loser.seasonWinStreak = 0;
         loser.lifetimeLosses++;
         loser.lifetimeGamesPlayed++;
      }
   }

   private String nameOf(UUID uuid) {
      DuelPlayerData data = this.dataManager.get(uuid);
      return data != null ? data.lastKnownName : "Unknown";
   }

   private void broadcastToMatch(DuelMatch match, Component component) {
      Player a = Bukkit.getPlayer(match.playerA);
      Player b = Bukkit.getPlayer(match.playerB);
      if (a != null) {
         a.sendMessage(component);
      }

      if (b != null) {
         b.sendMessage(component);
      }
   }

   public static enum Outcome {
      WIN,
      DRAW,
      FORFEIT;
   }
}
