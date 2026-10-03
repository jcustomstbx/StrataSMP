package com.stratasmp.strataduels.ffa;

import com.stratasmp.stratacore.StrataModule;
import com.stratasmp.strataduels.arena.Arena;
import com.stratasmp.strataduels.arena.ArenaManager;
import com.stratasmp.strataduels.arena.ArenaResetManager;
import com.stratasmp.strataduels.kit.KitManager;
import com.stratasmp.strataduels.match.MatchManager;
import com.stratasmp.strataduels.match.PendingRestores;
import com.stratasmp.strataduels.match.PlayerSnapshot;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Free-for-all rounds on an arena flagged with /duelarena ffa. Players queue with /ffa; once enough are
 * waiting a lobby countdown starts, everyone is snapshotted, kitted and dropped on the spawns, and the
 * last one standing wins. Separate from the 1v1 MatchManager on purpose - no ELO, no pairs.
 */
public class FfaManager {
   public enum State {
      WAITING,
      COUNTDOWN,
      ACTIVE,
      ENDING
   }

   private static final long HIT_CREDIT_MILLIS = 15000L;
   private static final Set<Integer> LOBBY_NOTICES = Set.of(60, 30, 20, 10, 5, 4, 3, 2, 1);

   private static final class Fighter {
      final UUID uuid;
      final String name;
      final PlayerSnapshot snapshot;
      final int kit;
      int kills;

      Fighter(UUID uuid, String name, PlayerSnapshot snapshot, int kit) {
         this.uuid = uuid;
         this.name = name;
         this.snapshot = snapshot;
         this.kit = kit;
      }
   }

   private record Hit(UUID attacker, long at) {
   }

   private final StrataModule plugin;
   private final ArenaManager arenas;
   private final ArenaResetManager resets;
   private final KitManager kits;
   private final MatchManager matches;
   private final Predicate<UUID> duelQueued;
   private final PendingRestores pending;
   private final Map<UUID, Integer> queue = new LinkedHashMap<>();
   private final Map<UUID, Fighter> fighters = new LinkedHashMap<>();
   private final Map<UUID, PlayerSnapshot> limbo = new HashMap<>();
   private final Set<UUID> frozen = new HashSet<>();
   private final Map<UUID, Hit> lastHit = new HashMap<>();
   private State state = State.WAITING;
   private Arena arena;
   private BukkitTask lobbyTask;
   private BukkitTask countdownTask;
   private BukkitTask timeoutTask;
   private int lobbySecondsLeft;

   public FfaManager(StrataModule plugin, ArenaManager arenas, ArenaResetManager resets, KitManager kits, MatchManager matches, Predicate<UUID> duelQueued) {
      this.plugin = plugin;
      this.arenas = arenas;
      this.resets = resets;
      this.kits = kits;
      this.matches = matches;
      this.duelQueued = duelQueued;
      this.pending = new PendingRestores(plugin);
   }

   private int minPlayers() {
      return Math.max(2, this.plugin.getConfig().getInt("ffa.min-players", 2));
   }

   /** 0 (the default) means no cap: everyone who joined before the timer ran out is teleported in. */
   private int maxPlayers() {
      int configured = this.plugin.getConfig().getInt("ffa.max-players", 0);
      return configured <= 0 ? Integer.MAX_VALUE : Math.max(this.minPlayers(), configured);
   }

   /** Seconds left on the lobby timer, or -1 when no timer is running. */
   public int lobbySeconds() {
      return this.lobbyTask != null ? this.lobbySecondsLeft : -1;
   }

   public int forcedKit() {
      return this.plugin.getConfig().getInt("ffa.forced-kit", 0);
   }

   /** The kits a player can pick in the FFA: ffa.kits from the config, or every kit that is set up when that list is empty. */
   public List<Integer> allowedKits() {
      List<Integer> wanted = new ArrayList<>(this.plugin.getConfig().getIntegerList("ffa.kits"));
      if (wanted.isEmpty()) {
         for (int kit = 1; kit <= KitManager.MAX_KITS; kit++) {
            wanted.add(kit);
         }
      }
      List<Integer> usable = new ArrayList<>();
      for (int kit : wanted) {
         if (kit >= 1 && kit <= KitManager.MAX_KITS && this.kits.hasKit(kit) && !usable.contains(kit)) {
            usable.add(kit);
         }
      }
      return usable;
   }

   /** Optional short names for the kit picker, from ffa.kit-names. */
   public Map<Integer, String> kitNames() {
      Map<Integer, String> names = new HashMap<>();
      var section = this.plugin.getConfig().getConfigurationSection("ffa.kit-names");
      if (section != null) {
         for (String key : section.getKeys(false)) {
            try {
               names.put(Integer.parseInt(key), section.getString(key));
            } catch (NumberFormatException ignored) {
            }
         }
      }
      return names;
   }

   public State state() {
      return this.state;
   }

   public Arena arena() {
      return this.arena;
   }

   public int queueSize() {
      return this.queue.size();
   }

   public boolean isQueued(UUID id) {
      return this.queue.containsKey(id);
   }

   /** True when a playable FFA map exists, so menus can say so instead of letting a player queue for nothing. */
   public boolean available() {
      Arena map = this.configuredMap();
      return map != null && this.playable(map);
   }

   /** What to tell a player right after they queue. */
   public String joinSummary() {
      if (this.state != State.WAITING) {
         return " - a round is in progress, you'll join the next one.";
      }
      int seconds = this.lobbySeconds();
      return (seconds >= 0 ? " - the FFA starts in " + seconds + "s, everyone queued is teleported in together" : "") + " (" + this.queue.size() + " queued).";
   }

   public int fightersLeft() {
      return this.fighters.size();
   }

   public int neededToStart() {
      return this.minPlayers();
   }

   public boolean isFighter(UUID id) {
      return this.fighters.containsKey(id);
   }

   public boolean isLimbo(UUID id) {
      return this.limbo.containsKey(id);
   }

   public boolean isFrozen(UUID id) {
      return this.frozen.contains(id);
   }

   public boolean isActive() {
      return this.state == State.ACTIVE;
   }

   public boolean isInvolved(UUID id) {
      return this.queue.containsKey(id) || this.fighters.containsKey(id) || this.limbo.containsKey(id);
   }

   public PlayerSnapshot limboSnapshot(UUID id) {
      return this.limbo.get(id);
   }

   private Arena configuredMap() {
      return this.arenas.list().stream().filter(a -> a.ffa && a.enabled).findFirst().orElse(null);
   }

   private boolean playable(Arena a) {
      return a.isReady() && a.hasBounds() && a.ffaSpawns().size() >= 2 && this.resets.hasBaseline(a);
   }

   public String join(Player player, int kit) {
      UUID id = player.getUniqueId();
      if (this.fighters.containsKey(id) || this.limbo.containsKey(id)) {
         return "You're already in the FFA.";
      }
      if (this.queue.containsKey(id)) {
         return "You're already queued - use /ffa leave to leave.";
      }
      if (this.matches.isInMatch(id)) {
         return "You're in a duel right now.";
      }
      if (this.duelQueued.test(id)) {
         return "Leave the duel queue first (/duel cancel).";
      }
      if (this.forcedKit() <= 0 && !this.allowedKits().contains(kit)) {
         return "That kit isn't available in the FFA - pick one of: " + this.allowedKits() + ".";
      }
      Arena map = this.configuredMap();
      if (map == null) {
         return "There's no FFA map set up yet.";
      }
      if (!this.playable(map)) {
         return "The FFA map isn't finished being set up yet - ask an admin.";
      }
      this.queue.put(id, kit);
      Bukkit.getScheduler().runTask(this.plugin, this::checkStart);
      return null;
   }

   /** Returns a message for the player, or null if there was nothing to leave. */
   public String leave(UUID id) {
      if (this.queue.remove(id) != null) {
         return "Left the FFA queue.";
      }
      if (this.fighters.containsKey(id)) {
         if (this.state == State.ACTIVE || this.state == State.COUNTDOWN) {
            this.eliminate(id, null, "left the FFA");
            return "You left the FFA - your inventory is being restored.";
         }
         return "The round is ending - you'll be sent back in a moment.";
      }
      return null;
   }

   public void handleQuit(UUID id) {
      this.queue.remove(id);
      if (this.fighters.containsKey(id) && this.state != State.ENDING) {
         this.eliminate(id, null, "disconnected");
      }
   }

   private void checkStart() {
      this.queue.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
      if (this.state != State.WAITING || this.lobbyTask != null || this.queue.isEmpty()) {
         return;
      }
      this.startLobbyTimer();
   }

   /** The timer starts with the first player to queue; everyone who joins before it hits zero is teleported in together. */
   private void startLobbyTimer() {
      this.resetLobbySeconds();
      if (this.plugin.getConfig().getBoolean("ffa.announce-server", true)) {
         Bukkit.broadcast(Component.text("A free-for-all starts in " + this.lobbySecondsLeft + " seconds! Type /ffa to join the fight.", NamedTextColor.GOLD));
      }
      this.lobbyTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::lobbyTick, 0L, 20L);
   }

   private void resetLobbySeconds() {
      this.lobbySecondsLeft = Math.max(3, this.plugin.getConfig().getInt("ffa.lobby-countdown-seconds", 30));
   }

   private void lobbyTick() {
      this.queue.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
      if (this.state != State.WAITING || this.queue.isEmpty()) {
         this.cancelLobby();
         return;
      }
      if (this.queue.size() >= this.maxPlayers() && this.lobbySecondsLeft > 5) {
         this.lobbySecondsLeft = 5;
      }
      if (this.lobbySecondsLeft <= 0) {
         if (this.queue.size() < this.minPlayers()) {
            this.notifyQueue("Not enough players yet (" + this.queue.size() + "/" + this.minPlayers() + ") - the timer has restarted, hang tight.");
            this.resetLobbySeconds();
            return;
         }
         this.cancelLobby();
         this.beginRound();
         return;
      }
      if (LOBBY_NOTICES.contains(this.lobbySecondsLeft)) {
         this.notifyQueue("FFA starting in " + this.lobbySecondsLeft + "s (" + this.queue.size() + " queued).");
      }
      if (this.lobbySecondsLeft == 10 && this.plugin.getConfig().getBoolean("ffa.announce-server", true)) {
         Bukkit.broadcast(Component.text("FFA starts in 10 seconds - /ffa to join now!", NamedTextColor.GOLD));
      }
      this.lobbySecondsLeft--;
   }

   private void cancelLobby() {
      if (this.lobbyTask != null) {
         this.lobbyTask.cancel();
         this.lobbyTask = null;
      }
   }

   /** Admin shortcut: skip the lobby countdown. Returns an error message or null. */
   public String forceStart() {
      if (this.state != State.WAITING) {
         return "A round is already running.";
      }
      this.queue.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
      if (this.queue.size() < this.minPlayers()) {
         return "Need at least " + this.minPlayers() + " queued players (" + this.queue.size() + " now).";
      }
      this.cancelLobby();
      this.beginRound();
      return null;
   }

   /** Admin shortcut: end the running round with no winner. Returns an error message or null. */
   public String forceStop() {
      if (this.state == State.WAITING) {
         return "No FFA round is running.";
      }
      if (this.state == State.ENDING) {
         return "The round is already ending.";
      }
      this.broadcastFighters(Component.text("An admin ended the FFA.", NamedTextColor.GRAY));
      this.endRound(null);
      return null;
   }

   private void beginRound() {
      Arena map = this.configuredMap();
      if (map == null || !this.playable(map) || map.inUse) {
         this.notifyQueue("The FFA map isn't available right now - staying in the queue.");
         return;
      }
      List<UUID> chosen = new ArrayList<>();
      for (UUID id : this.queue.keySet()) {
         Player p = Bukkit.getPlayer(id);
         if (p != null && !this.matches.isInMatch(id)) {
            chosen.add(id);
         }
         if (chosen.size() >= this.maxPlayers()) {
            break;
         }
      }
      if (chosen.size() < this.minPlayers()) {
         this.notifyQueue("Not enough players for the FFA - waiting for more.");
         return;
      }

      this.arena = map;
      this.arenas.markInUse(map, true);
      this.state = State.COUNTDOWN;
      List<Location> spawns = new ArrayList<>(map.ffaSpawns());
      Collections.shuffle(spawns);
      int index = 0;
      for (UUID id : chosen) {
         Player player = Bukkit.getPlayer(id);
         int chosenKit = this.queue.remove(id);
         int kit = this.forcedKit() > 0 ? this.forcedKit() : chosenKit;
         // close first: an open trade window hands its items back on close, before the snapshot is taken
         player.closeInventory();
         PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
         this.pending.save(id, snapshot);
         this.fighters.put(id, new Fighter(id, player.getName(), snapshot, kit));
         this.frozen.add(id);
         player.setGameMode(GameMode.SURVIVAL);
         player.setFireTicks(0);
         player.setFallDistance(0.0F);
         player.teleport(spawns.get(index % spawns.size()));
         index++;
      }
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         for (Fighter f : this.fighters.values()) {
            Player p = Bukkit.getPlayer(f.uuid);
            if (p != null) {
               this.healToFull(p);
               this.kits.applyKit(f.kit, p);
               p.updateInventory();
            }
         }
      });
      this.broadcastFighters(Component.text("Free-for-all at " + map.name + " - " + chosen.size() + " fighters. Last one standing wins!", NamedTextColor.GOLD));
      int seconds = Math.max(1, this.plugin.getConfig().getInt("ffa.countdown-seconds", 5));
      this.countdownTask = Bukkit.getScheduler().runTaskTimer(this.plugin, new Runnable() {
         int left = seconds;

         @Override
         public void run() {
            if (FfaManager.this.state != State.COUNTDOWN) {
               FfaManager.this.countdownTask.cancel();
               return;
            }
            if (this.left <= 0) {
               FfaManager.this.countdownTask.cancel();
               FfaManager.this.beginActive();
               return;
            }
            FfaManager.this.broadcastFighters(Component.text(this.left + "...", NamedTextColor.YELLOW));
            this.left--;
         }
      }, 20L, 20L);
   }

   private void beginActive() {
      this.state = State.ACTIVE;
      this.frozen.clear();
      for (UUID id : this.fighters.keySet()) {
         Player p = Bukkit.getPlayer(id);
         if (p != null) {
            p.setMetadata(MatchManager.DUEL_TAG_KEY, new FixedMetadataValue(this.plugin, true));
            p.showTitle(Title.title(Component.text("FIGHT!", NamedTextColor.RED), Component.empty()));
         }
      }
      int maxSeconds = this.plugin.getConfig().getInt("ffa.max-duration-seconds", 600);
      this.timeoutTask = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         if (this.state == State.ACTIVE) {
            this.broadcastFighters(Component.text("Time's up - nobody won the FFA.", NamedTextColor.GRAY));
            this.endRound(null);
         }
      }, maxSeconds * 20L);
      if (this.fighters.size() <= 1) {
         this.endRound(this.fighters.isEmpty() ? null : this.fighters.keySet().iterator().next());
      }
   }

   public void recordHit(UUID victim, UUID attacker) {
      if (!victim.equals(attacker)) {
         this.lastHit.put(victim, new Hit(attacker, System.currentTimeMillis()));
      }
   }

   private UUID killerFor(UUID victim) {
      Hit hit = this.lastHit.get(victim);
      if (hit == null || System.currentTimeMillis() - hit.at() > HIT_CREDIT_MILLIS || !this.fighters.containsKey(hit.attacker())) {
         return null;
      }
      return hit.attacker();
   }

   /** A fighter took a killing blow (or died by other means): out of the round, kit gone, sent back. */
   public void handleLethal(UUID victim, UUID attacker) {
      if (this.state == State.ACTIVE && this.fighters.containsKey(victim)) {
         UUID killer = attacker != null && this.fighters.containsKey(attacker) && !attacker.equals(victim) ? attacker : this.killerFor(victim);
         this.eliminate(victim, killer, null);
      }
   }

   private void eliminate(UUID id, UUID killerId, String cause) {
      Fighter out = this.fighters.remove(id);
      if (out == null) {
         return;
      }
      this.frozen.remove(id);
      this.lastHit.remove(id);
      this.limbo.put(id, out.snapshot);
      this.clearTag(id);
      this.matches.clearCombatTags(id);
      int placement = this.fighters.size() + 1;
      Fighter killer = killerId == null ? null : this.fighters.get(killerId);
      if (killer != null) {
         killer.kills++;
      }
      String what = killer != null ? out.name + " was eliminated by " + killer.name : out.name + " " + (cause != null ? cause : "was eliminated");
      Component message = Component.text(what + " - " + this.fighters.size() + " left.", NamedTextColor.RED);
      this.broadcastFighters(message);
      Player victim = Bukkit.getPlayer(id);
      if (victim != null) {
         victim.sendMessage(message);
         victim.showTitle(Title.title(Component.text("ELIMINATED", NamedTextColor.RED), Component.text("You placed #" + placement, NamedTextColor.GRAY)));
      }
      this.restoreLater(id, 0);

      if (this.state == State.ACTIVE && this.fighters.size() <= 1) {
         this.endRound(this.fighters.isEmpty() ? null : this.fighters.keySet().iterator().next());
      } else if (this.state == State.COUNTDOWN && this.fighters.size() < 2) {
         this.abortRound();
      }
   }

   /** Restores an eliminated fighter a tick later (never mid-damage-event), waiting out a death screen if there is one. */
   private void restoreLater(UUID id, int attempt) {
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         PlayerSnapshot snapshot = this.limbo.get(id);
         if (snapshot == null) {
            return;
         }
         Player p = Bukkit.getPlayer(id);
         if (p != null && p.isDead() && attempt < 40) {
            this.restoreLater(id, attempt + 1);
            return;
         }
         this.limbo.remove(id);
         this.restore(id, snapshot);
      }, 1L);
   }

   private void restore(UUID id, PlayerSnapshot snapshot) {
      Player p = Bukkit.getPlayer(id);
      if (p == null) {
         this.pending.save(id, snapshot);
         return;
      }
      p.teleport(snapshot.location());
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         if (p.isOnline()) {
            snapshot.applyState(p);
            this.pending.discard(id);
         } else {
            this.pending.save(id, snapshot);
         }
      });
   }

   private void abortRound() {
      this.broadcastFighters(Component.text("Not enough fighters left - the FFA was called off.", NamedTextColor.GRAY));
      this.endRound(null);
   }

   private void endRound(UUID winnerId) {
      if (this.state == State.ENDING) {
         return;
      }
      this.state = State.ENDING;
      this.cancel(this.timeoutTask);
      this.cancel(this.countdownTask);
      this.frozen.addAll(this.fighters.keySet());
      Fighter winner = winnerId == null ? null : this.fighters.get(winnerId);
      if (winner != null) {
         Component line = Component.text(winner.name + " won the free-for-all with " + winner.kills + " kill" + (winner.kills == 1 ? "" : "s") + "!", NamedTextColor.GOLD);
         if (this.plugin.getConfig().getBoolean("ffa.announce-server", true)) {
            Bukkit.broadcast(line);
         } else {
            this.broadcastFighters(line);
         }
         Player p = Bukkit.getPlayer(winner.uuid);
         if (p != null) {
            p.showTitle(Title.title(Component.text("VICTORY!", NamedTextColor.GOLD), Component.text("Last one standing", NamedTextColor.YELLOW),
               Title.Times.times(Duration.ofMillis(200L), Duration.ofSeconds(2L), Duration.ofMillis(500L))));
            this.fireworks(p);
         }
      }
      long delay = Math.max(1, this.plugin.getConfig().getInt("ffa.end-delay-seconds", 5)) * 20L;
      Bukkit.getScheduler().runTaskLater(this.plugin, this::finishRound, delay);
   }

   private void finishRound() {
      for (UUID id : new ArrayList<>(this.fighters.keySet())) {
         Fighter f = this.fighters.remove(id);
         this.clearTag(id);
         this.matches.clearCombatTags(id);
         this.restore(id, f.snapshot);
      }
      this.frozen.clear();
      this.lastHit.clear();
      Arena finished = this.arena;
      if (finished == null) {
         this.state = State.WAITING;
         return;
      }
      this.resets.resetArena(finished, () -> {
         this.arenas.markInUse(finished, false);
         this.arena = null;
         this.state = State.WAITING;
         this.checkStart();
      });
   }

   /** Server stop or plugin reload: put everyone back right now, synchronously. */
   public void shutdown() {
      this.cancelLobby();
      this.cancel(this.timeoutTask);
      this.cancel(this.countdownTask);
      Map<UUID, PlayerSnapshot> everyone = new HashMap<>(this.limbo);
      for (Fighter f : this.fighters.values()) {
         everyone.put(f.uuid, f.snapshot);
      }
      for (Map.Entry<UUID, PlayerSnapshot> e : everyone.entrySet()) {
         this.clearTag(e.getKey());
         Player p = Bukkit.getPlayer(e.getKey());
         if (p == null) {
            this.pending.save(e.getKey(), e.getValue());
         } else {
            p.teleport(e.getValue().location());
            e.getValue().applyState(p);
            this.pending.discard(e.getKey());
         }
      }
      if (this.arena != null) {
         this.resets.resetArenaBlocking(this.arena);
         this.arenas.markInUse(this.arena, false);
      }
      this.fighters.clear();
      this.limbo.clear();
      this.frozen.clear();
      this.queue.clear();
      this.arena = null;
      this.state = State.WAITING;
   }

   private void cancel(BukkitTask task) {
      if (task != null) {
         task.cancel();
      }
   }

   private void clearTag(UUID id) {
      Player p = Bukkit.getPlayer(id);
      if (p != null) {
         p.removeMetadata(MatchManager.DUEL_TAG_KEY, this.plugin);
      }
   }

   private void healToFull(Player player) {
      AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
      player.setHealth(max != null ? max.getValue() : 20.0);
      player.setFoodLevel(20);
      player.setSaturation(20.0F);
   }

   private void broadcastFighters(Component message) {
      for (UUID id : this.fighters.keySet()) {
         Player p = Bukkit.getPlayer(id);
         if (p != null) {
            p.sendMessage(message);
         }
      }
   }

   private void notifyQueue(String text) {
      Component message = Component.text(text, NamedTextColor.YELLOW);
      for (UUID id : this.queue.keySet()) {
         Player p = Bukkit.getPlayer(id);
         if (p != null) {
            p.sendMessage(message);
         }
      }
   }

   private void fireworks(Player winner) {
      Color[] colors = {Color.YELLOW, Color.ORANGE, Color.WHITE, Color.RED, Color.LIME};
      FireworkEffect.Type[] types = {FireworkEffect.Type.BALL_LARGE, FireworkEffect.Type.BURST, FireworkEffect.Type.STAR};
      new BukkitRunnable() {
         int fired = 0;

         @Override
         public void run() {
            if (this.fired >= 8 || !winner.isOnline()) {
               this.cancel();
               return;
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Location at = winner.getLocation().add(random.nextDouble(-1.5, 1.5), 0.2, random.nextDouble(-1.5, 1.5));
            Firework firework = winner.getWorld().spawn(at, Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().withColor(colors[random.nextInt(colors.length)]).withFade(Color.WHITE)
               .with(types[random.nextInt(types.length)]).flicker(true).build());
            meta.setPower(1);
            firework.setFireworkMeta(meta);
            this.fired++;
         }
      }.runTaskTimer(this.plugin, 0L, 8L);
   }
}
