package com.stratasmp.strataservices;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** How many of each service a player has bought today. Counts reset when the date changes. */
public final class PurchaseLedger {
    private record Day(LocalDate date, Map<String, Integer> counts) {}

    private final Map<UUID, Day> days = new HashMap<>();

    public int boughtToday(UUID player, String service, LocalDate today) {
        Day day = days.get(player);
        if (day == null || !day.date.equals(today)) return 0;
        return day.counts.getOrDefault(service, 0);
    }

    public void record(UUID player, String service, LocalDate today) {
        Day day = days.get(player);
        if (day == null || !day.date.equals(today)) {
            day = new Day(today, new HashMap<>());
            days.put(player, day);
        }
        day.counts.merge(service, 1, Integer::sum);
    }

    public void undo(UUID player, String service, LocalDate today) {
        Day day = days.get(player);
        if (day == null || !day.date.equals(today)) return;
        day.counts.computeIfPresent(service, (k, v) -> v <= 1 ? null : v - 1);
    }

    /** A new copy of the data, safe to hand to another thread. */
    public YamlConfiguration snapshot(LocalDate today) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Day> entry : days.entrySet()) {
            Day day = entry.getValue();
            if (!day.date.equals(today) || day.counts.isEmpty()) continue;
            String base = entry.getKey().toString();
            yaml.set(base + ".date", day.date.toString());
            for (Map.Entry<String, Integer> count : day.counts.entrySet()) {
                yaml.set(base + ".counts." + count.getKey(), count.getValue());
            }
        }
        return yaml;
    }

    public void load(YamlConfiguration yaml) {
        days.clear();
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null) continue;
            try {
                UUID id = UUID.fromString(key);
                LocalDate date = LocalDate.parse(section.getString("date", ""));
                Map<String, Integer> counts = new HashMap<>();
                ConfigurationSection saved = section.getConfigurationSection("counts");
                if (saved != null) {
                    for (String service : saved.getKeys(false)) counts.put(service, saved.getInt(service));
                }
                days.put(id, new Day(date, counts));
            } catch (IllegalArgumentException | java.time.format.DateTimeParseException ignored) {
                // a damaged entry only costs that player a reset limit
            }
        }
    }
}
