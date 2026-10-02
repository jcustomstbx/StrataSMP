package com.stratasmp.strataleaderboards;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kill/death streaks aren't a vanilla statistic (unlike total kills, deaths and playtime, which Minecraft
 * already tracks per player), so this is the one board that needs its own bookkeeping: a player's current
 * streak resets to zero on death and their best is kept alongside it, in {@code streaks.yml}.
 */
public final class StreakTracker {

    private record Streak(long current, long best) {
    }

    private final File file;
    private final Map<UUID, Streak> streaks = new LinkedHashMap<>();

    public StreakTracker(File file) {
        this.file = file;
    }

    public synchronized void load() {
        streaks.clear();
        var config = YamlConfiguration.loadConfiguration(file);
        var section = config.getConfigurationSection("streaks");
        if (section == null) {
            return;
        }
        for (var id : section.getKeys(false)) {
            try {
                streaks.put(UUID.fromString(id), new Streak(section.getLong(id + ".current"), section.getLong(id + ".best")));
            } catch (IllegalArgumentException ignored) {
                // a damaged entry is dropped rather than crashing the load
            }
        }
    }

    public synchronized void recordDeath(UUID playerId) {
        var previous = streaks.get(playerId);
        if (previous == null || previous.current() == 0) {
            return;
        }
        streaks.put(playerId, new Streak(0, previous.best()));
        save();
    }

    public synchronized void recordKill(UUID killerId) {
        var previous = streaks.getOrDefault(killerId, new Streak(0, 0));
        var current = previous.current() + 1;
        streaks.put(killerId, new Streak(current, Math.max(current, previous.best())));
        save();
    }

    public synchronized List<Row> top(int limit) {
        return streaks.entrySet().stream()
            .filter(entry -> entry.getValue().best() > 0)
            .sorted(Comparator.<Map.Entry<UUID, Streak>>comparingLong(entry -> entry.getValue().best()).reversed())
            .limit(limit)
            .map(entry -> new Row(entry.getKey(), "", entry.getValue().best()))
            .toList();
    }

    private void save() {
        var config = new YamlConfiguration();
        for (var entry : streaks.entrySet()) {
            var path = "streaks." + entry.getKey();
            config.set(path + ".current", entry.getValue().current());
            config.set(path + ".best", entry.getValue().best());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save streaks.yml", e);
        }
    }
}
