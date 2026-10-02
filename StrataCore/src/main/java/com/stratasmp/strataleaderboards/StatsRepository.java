package com.stratasmp.strataleaderboards;

import com.stratasmp.strataeconomy.api.StrataApi;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Where each board's numbers come from. Kills, deaths and playtime are vanilla's own per-player statistics -
 * nothing here writes to them, Minecraft has counted them since the player's first join. Stratas comes from
 * StrataEconomy' own balance service. Streak is the one number nothing else tracks, so it's read from
 * {@link StreakTracker} instead.
 */
public final class StatsRepository {

    private final Logger logger;

    public StatsRepository(Logger logger) {
        this.logger = logger;
    }

    public List<Row> top(BoardType type, int limit, StreakTracker streaks) {
        return switch (type) {
            case KILLS -> fromStatistic(Statistic.PLAYER_KILLS, limit, 1);
            case DEATHS -> fromStatistic(Statistic.DEATHS, limit, 1);
            case PLAYTIME -> fromStatistic(Statistic.PLAY_ONE_MINUTE, limit, 20); // stat is ticks, not minutes
            case STREAK -> streaks.top(limit);
            case STRATAS -> stratasTop(limit);
        };
    }

    private List<Row> fromStatistic(Statistic statistic, int limit, int divisor) {
        var rows = new ArrayList<Row>();
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            var uuid = player.getUniqueId();
            long raw;
            try {
                raw = player.getStatistic(statistic);
            } catch (RuntimeException e) {
                continue; // a corrupt or unreadable playerdata file shouldn't take the whole board down
            }
            var value = raw / divisor;
            if (value > 0) {
                rows.add(new Row(uuid, "", value));
            }
        }
        rows.sort(Comparator.<Row>comparingLong(Row::value).reversed());
        return rows.size() > limit ? rows.subList(0, limit) : rows;
    }

    private List<Row> stratasTop(int limit) {
        var stratas = StrataApi.get();
        if (stratas == null) {
            logger.warning("StrataApi isn't registered yet, the Stratas board will read empty until it is.");
            return List.of();
        }
        return stratas.top(limit).stream()
            .filter(entry -> entry.getValue() > 0)
            .map(entry -> new Row(entry.getKey(), "", entry.getValue()))
            .toList();
    }
}
