package com.stratasmp.strataleaderboards;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Total kills, deaths and playtime are read straight from vanilla's own per-player statistics - only the
 * kill-streak board needs a listener, since "consecutive kills without dying" isn't something vanilla tracks. */
final class StreakListener implements Listener {

    private final StreakTracker streaks;

    StreakListener(StreakTracker streaks) {
        this.streaks = streaks;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        streaks.recordDeath(victim.getUniqueId());
        Player killer = victim.getKiller();
        if (killer != null) {
            streaks.recordKill(killer.getUniqueId());
        }
    }
}
