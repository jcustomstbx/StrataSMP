package com.stratasmp.strataevents;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Consecutive-day login streaks. One claim per calendar day; missing a day starts again at 1. */
public final class LoginStreaks {
    private record State(LocalDate lastClaim, int streak, int best) {}

    public record Claim(int streak, int best) {}

    private final Map<UUID, State> states = new HashMap<>();

    /** @return the new streak, or null if the player already claimed today. */
    public Claim claim(UUID player, LocalDate today) {
        State state = states.get(player);
        if (state != null && !state.lastClaim.isBefore(today)) return null;
        int streak = state != null && state.lastClaim.plusDays(1).equals(today) ? state.streak + 1 : 1;
        int best = Math.max(streak, state == null ? 0 : state.best);
        states.put(player, new State(today, streak, best));
        return new Claim(streak, best);
    }

    public boolean claimedToday(UUID player, LocalDate today) {
        State state = states.get(player);
        return state != null && !state.lastClaim.isBefore(today);
    }

    /** The streak that is still alive: yesterday's or today's claim counts, anything older is broken. */
    public int currentStreak(UUID player, LocalDate today) {
        State state = states.get(player);
        if (state == null || state.lastClaim.plusDays(1).isBefore(today)) return 0;
        return state.streak;
    }

    public int best(UUID player) {
        State state = states.get(player);
        return state == null ? 0 : state.best;
    }

    /** Stratas for a day of the streak: base + perDay for each day, levelling off after {@code maxDay}. */
    public static long stratasFor(int streak, long base, long perDay, int maxDay) {
        int day = Math.max(1, Math.min(streak, Math.max(1, maxDay)));
        return Math.max(0, base + Math.max(0, perDay) * (day - 1));
    }

    public YamlConfiguration snapshot() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, State> entry : states.entrySet()) {
            String base = entry.getKey().toString();
            yaml.set(base + ".last", entry.getValue().lastClaim.toString());
            yaml.set(base + ".streak", entry.getValue().streak);
            yaml.set(base + ".best", entry.getValue().best);
        }
        return yaml;
    }

    public void load(YamlConfiguration yaml) {
        states.clear();
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null) continue;
            try {
                states.put(UUID.fromString(key), new State(LocalDate.parse(section.getString("last", "")),
                        Math.max(1, section.getInt("streak", 1)), Math.max(1, section.getInt("best", 1))));
            } catch (IllegalArgumentException | java.time.format.DateTimeParseException ignored) {
                // a damaged entry only costs that player their streak
            }
        }
    }
}
