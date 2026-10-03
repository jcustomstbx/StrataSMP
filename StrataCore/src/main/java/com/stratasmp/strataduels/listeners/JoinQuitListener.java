package com.stratasmp.strataduels.listeners;

import com.stratasmp.strataduels.DataManager;
import com.stratasmp.strataduels.challenge.ChallengeManager;
import com.stratasmp.strataduels.ffa.FfaManager;
import com.stratasmp.strataduels.match.MatchManager;
import com.stratasmp.strataduels.queue.QueueManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinQuitListener implements Listener {
   private final DataManager dataManager;
   private final MatchManager matchManager;
   private final QueueManager queueManager;
   private final ChallengeManager challengeManager;
   private final FfaManager ffaManager;

   public JoinQuitListener(DataManager dataManager, MatchManager matchManager, QueueManager queueManager, ChallengeManager challengeManager, FfaManager ffaManager) {
      this.ffaManager = ffaManager;
      this.dataManager = dataManager;
      this.matchManager = matchManager;
      this.queueManager = queueManager;
      this.challengeManager = challengeManager;
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      this.dataManager.load(event.getPlayer());
      this.matchManager.applyPendingRestoreIfAny(event.getPlayer());
   }

   /** A duelist who died during the end delay has their inventory restored once they are alive again. */
   @EventHandler
   public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
      // the restore is applied a tick later, once the player is alive
      this.matchManager.applyPendingRestoreIfAny(event.getPlayer());
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.matchManager.forfeit(event.getPlayer().getUniqueId());
      this.ffaManager.handleQuit(event.getPlayer().getUniqueId());
      this.queueManager.leave(event.getPlayer().getUniqueId());
      this.challengeManager.deny(event.getPlayer().getUniqueId());
      this.challengeManager.cancelOutgoing(event.getPlayer().getUniqueId());
      this.dataManager.unload(event.getPlayer());
   }
}
