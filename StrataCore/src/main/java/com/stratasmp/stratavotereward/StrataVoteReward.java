package com.stratasmp.stratavotereward;

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;


public class StrataVoteReward extends StrataModule {
   public StrataVoteReward(StrataCore core) {
      super(core, "StrataVoteReward");
   }

   public void onEnable() {
      this.saveDefaultConfig();
      PendingVotes pendingVotes = new PendingVotes(this);
      ProcessedVotes processedVotes = new ProcessedVotes(this);
      this.getServer().getPluginManager().registerEvents(
            new VoteListener(this, pendingVotes, new VoteCounts(this), processedVotes), this);
      VoteReminder.start(this);
      this.getCommand("vote").setExecutor(new VoteCommand(this));
      this.getLogger().info("StrataVoteReward enabled - listening for votes.");
   }
}
