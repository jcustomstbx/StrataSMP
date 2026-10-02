package com.stratasmp.strataleaderboards;

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.Bukkit;

import java.io.File;

/** Floating leaderboards for kills, kill streaks, deaths, playtime and Stratas, ported from a sibling project
 * and rebuilt against StrataSMP's own data - vanilla per-player statistics for kills/deaths/playtime, StrataEconomy'
 * balance service for Stratas, and a small streak tracker of its own since a kill streak isn't a vanilla stat. */
public final class StrataLeaderboards extends StrataModule {

    private LeaderboardService service;

    public StrataLeaderboards(StrataCore core) {
        super(core, "StrataLeaderboards");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        var store = new BoardStore(new File(getDataFolder(), "boards.yml"));
        store.load();
        var streaks = new StreakTracker(new File(getDataFolder(), "streaks.yml"));
        streaks.load();
        var repository = new StatsRepository(getLogger());
        this.service = new LeaderboardService(this, store, repository, streaks);

        getServer().getPluginManager().registerEvents(new StreakListener(streaks), this);

        var command = getCommand("leaderboard");
        if (command != null) {
            var handler = new LeaderboardCommand(this, service);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        var seconds = Math.max(10, getConfig().getInt("refresh-seconds", 60));
        Bukkit.getScheduler().runTaskTimer(this, service::refreshAll, 40L, seconds * 20L);

        getLogger().info("StrataLeaderboards enabled - " + store.all().size() + " board(s) placed.");
    }
}
