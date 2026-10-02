package com.stratasmp.strataduels;

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;

import com.stratasmp.strataduels.arena.ArenaCommand;
import com.stratasmp.strataduels.arena.ArenaManager;
import com.stratasmp.strataduels.arena.ArenaResetManager;
import com.stratasmp.strataduels.challenge.ChallengeManager;
import com.stratasmp.strataduels.command.DuelCommand;
import com.stratasmp.strataduels.ffa.FfaCommand;
import com.stratasmp.strataduels.ffa.FfaListener;
import com.stratasmp.strataduels.ffa.FfaManager;
import com.stratasmp.strataduels.papi.DuelsExpansion;
import com.stratasmp.strataduels.gui.DuelMainMenuGUI;
import com.stratasmp.strataduels.gui.DuelPlayerPickerGUI;
import com.stratasmp.strataduels.gui.KitSelectGUI;
import com.stratasmp.strataduels.gui.LeaderboardGUI;
import com.stratasmp.strataduels.kit.KitCommand;
import com.stratasmp.strataduels.kit.KitManager;
import com.stratasmp.strataduels.listeners.JoinQuitListener;
import com.stratasmp.strataduels.match.DuelListener;
import com.stratasmp.strataduels.match.MatchManager;
import com.stratasmp.strataduels.queue.QueueManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class StrataDuels extends StrataModule {
   public StrataDuels(StrataCore core) {
      super(core, "StrataDuels");
   }

   private DataManager dataManager;
   private ArenaManager arenaManager;
   private ArenaResetManager arenaResetManager;
   private KitManager kitManager;
   private MatchManager matchManager;
   private QueueManager queueManager;
   private FfaManager ffaManager;
   private ChallengeManager challengeManager;
   private LeaderboardGUI leaderboardGUI;
   private KitSelectGUI kitSelectGUI;
   private SeasonManager seasonManager;
   private DuelPlayerPickerGUI playerPickerGUI;
   private DuelMainMenuGUI mainMenuGUI;
   private DuelsExpansion duelsExpansion;

   public void onEnable() {
      this.saveDefaultConfig();
      double startingElo = this.getConfig().getDouble("elo.starting-elo", 1000.0);
      this.dataManager = new DataManager(this, startingElo);
      this.arenaManager = new ArenaManager(this);
      this.arenaResetManager = new ArenaResetManager(this);
      this.arenaResetManager.loadCages(this.arenaManager.list());
      this.kitManager = new KitManager(this);
      this.matchManager = new MatchManager(this, this.arenaManager, this.arenaResetManager, this.kitManager, this.dataManager);
      this.queueManager = new QueueManager(this, this.matchManager, this.dataManager);
      this.matchManager.setOnArenaFreed(this.queueManager::checkForMatches);
      this.ffaManager = new FfaManager(this, this.arenaManager, this.arenaResetManager, this.kitManager, this.matchManager, this.queueManager::isQueued);
      this.matchManager.setExternalBusy(this.ffaManager::isInvolved);
      this.challengeManager = new ChallengeManager(this, this.matchManager, this.queueManager);
      this.leaderboardGUI = new LeaderboardGUI(this, this.dataManager);
      this.kitSelectGUI = new KitSelectGUI(this.kitManager);
      this.seasonManager = new SeasonManager(this, this.dataManager);
      this.playerPickerGUI = new DuelPlayerPickerGUI();
      this.mainMenuGUI = new DuelMainMenuGUI(
         this,
         this.queueManager,
         this.challengeManager,
         this.matchManager,
         this.dataManager,
         this.kitSelectGUI,
         this.leaderboardGUI,
         this.playerPickerGUI,
         this.seasonManager,
         this.ffaManager
      );
      this.queueManager.start();
      this.seasonManager.start();
      this.getServer()
         .getPluginManager()
         .registerEvents(new JoinQuitListener(this.dataManager, this.matchManager, this.queueManager, this.challengeManager, this.ffaManager), this);
      this.getServer().getPluginManager().registerEvents(new DuelListener(this.matchManager), this);
      this.getServer().getPluginManager().registerEvents(new FfaListener(this.ffaManager, this.matchManager), this);
      this.getServer().getPluginManager().registerEvents(this.leaderboardGUI, this);
      this.getServer().getPluginManager().registerEvents(this.kitSelectGUI, this);
      this.getServer().getPluginManager().registerEvents(this.playerPickerGUI, this);
      this.getServer().getPluginManager().registerEvents(this.mainMenuGUI, this);
      ArenaCommand arenaCommand = new ArenaCommand(this.arenaManager, this.arenaResetManager);
      this.getCommand("duelarena").setExecutor(arenaCommand);
      this.getCommand("duelarena").setTabCompleter(arenaCommand);
      KitCommand kitCommand = new KitCommand(this.kitManager);
      this.getCommand("duelkit").setExecutor(kitCommand);
      this.getCommand("duelkit").setTabCompleter(kitCommand);
      DuelCommand duelCommand = new DuelCommand(
         this,
         this.queueManager,
         this.challengeManager,
         this.kitManager,
         this.dataManager,
         this.leaderboardGUI,
         this.kitSelectGUI,
         this.seasonManager,
         this.mainMenuGUI,
         this.matchManager,
         this.arenaManager
      );
      this.getCommand("duel").setExecutor(duelCommand);
      this.getCommand("duel").setTabCompleter(duelCommand);
      FfaCommand ffaCommand = new FfaCommand(this, this.ffaManager, this.kitManager, this.kitSelectGUI);
      this.getCommand("ffa").setExecutor(ffaCommand);
      this.getCommand("ffa").setTabCompleter(ffaCommand);

      for (Player player : Bukkit.getOnlinePlayers()) {
         this.dataManager.load(player);
      }

      long autosaveTicks = this.getConfig().getLong("autosave-interval-minutes", 5L) * 60L * 20L;
      Bukkit.getScheduler().runTaskTimer(this, this.dataManager::saveAll, autosaveTicks, autosaveTicks);
      if (this.getConfig().getBoolean("web.push-enabled", false)) {
         String apiUrl = this.getConfig().getString("web.api-url", "");
         String apiKey = this.getConfig().getString("web.api-key", "");
         long pushTicks = this.getConfig().getLong("web.push-interval-seconds", 60L) * 20L;
         WebPusher pusher = new WebPusher(this, this.dataManager, apiUrl, apiKey);
         Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            this.dataManager.refreshLeaderboardCache();
            pusher.push();
         }, 20L, pushTicks);
      } else {
         long leaderboardTicks = this.getConfig().getLong("leaderboard-refresh-interval-seconds", 60L) * 20L;
         Bukkit.getScheduler().runTaskTimerAsynchronously(this, this.dataManager::refreshLeaderboardCache, 20L, leaderboardTicks);
      }

      if (this.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
         var papi = me.clip.placeholderapi.PlaceholderAPIPlugin.getInstance();
         if (papi != null) {
            var previous = papi.getLocalExpansionManager().getExpansion("strataduels");
            if (previous != null && previous.getClass().getName().equals(DuelsExpansion.class.getName())) previous.unregister();
         }
         this.duelsExpansion = new DuelsExpansion(this, this.dataManager);
         if (this.duelsExpansion.register()) this.getLogger().info("PlaceholderAPI expansion 'strataduels' registered.");
         else this.getLogger().warning("Couldn't register PlaceholderAPI expansion 'strataduels'.");
      }
   }

   public DataManager dataManager() {
      return this.dataManager;
   }

   public void onDisable() {
      if (this.duelsExpansion != null && this.duelsExpansion.isRegistered()) {
         this.duelsExpansion.unregister();
      }
      if (this.ffaManager != null) {
         this.ffaManager.shutdown();
      }

      if (this.matchManager != null) {
         this.matchManager.forceResolveAllActive();
      }

      if (this.queueManager != null) {
         this.queueManager.stop();
      }

      if (this.dataManager != null) {
         this.dataManager.saveAll();
      }
   }
}
