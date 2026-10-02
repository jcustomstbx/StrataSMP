package com.stratasmp.stratateams;

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;


public class StrataTeams extends StrataModule {
   public StrataTeams(StrataCore core) {
      super(core, "StrataTeams");
   }

   private CombatTracker combat;

   public CombatTracker getCombatTracker() {
      return this.combat;
   }

   public void onEnable() {
      this.saveDefaultConfig();
      TeamManager teams = new TeamManager(this);
      this.combat = new CombatTracker(this.getConfig().getLong("combat-tag-seconds", 10L));
      HomeTeleporter teleporter = new HomeTeleporter(this, teams, this.combat);
      TeamGUI gui = new TeamGUI(teams, teleporter);
      this.getServer().getPluginManager().registerEvents(gui, this);
      this.getServer().getPluginManager().registerEvents(teleporter, this);
      this.getServer().getPluginManager().registerEvents(new FriendlyFireListener(teams, this.combat), this);
      this.getServer().getPluginManager().registerEvents(new TeleportFlightGrace(this), this);
      this.getCommand("myteam").setExecutor(new TeamCommand(teams, gui));
      this.getCommand("thome").setExecutor(new HomeCommand(teams, teleporter));
      this.getLogger().info("StrataTeams enabled.");
   }
}
