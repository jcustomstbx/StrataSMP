package com.stratasmp.stratacore;

import com.stratasmp.strataevents.StrataEvents;
import com.stratasmp.stratammo.StrataMMO;
import com.stratasmp.stratammo.quests.QuestManager;
import com.stratasmp.strataweapons.StrataWeapons;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-module profile placeholders for TAB, scoreboards and holograms:
 * %strata_charms%, %strata_quests%, %strata_quests_done%, %strata_quests_total%, %strata_streak%,
 * %strata_kills%, %strata_deaths%. Called from other threads, so every lookup is null-safe and
 * falls back to the last value that was read.
 */
final class StrataExpansion extends PlaceholderExpansion {

    private final StrataCore core;
    private final Map<String, String> last = new ConcurrentHashMap<>();

    StrataExpansion(StrataCore core) {
        this.core = core;
    }

    @Override public @NotNull String getIdentifier() { return "strata"; }
    @Override public @NotNull String getAuthor() { return "JCustoms"; }
    @Override public @NotNull String getVersion() { return core.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        UUID id = player.getUniqueId();
        String key = params.toLowerCase();
        String value;
        try {
            value = switch (key) {
                case "charms" -> {
                    StrataWeapons weapons = core.module(StrataWeapons.class);
                    yield weapons == null ? "0" : String.valueOf(weapons.charmsOf(id));
                }
                case "quests", "quests_done", "quests_total" -> quests(id, key);
                case "streak" -> {
                    StrataEvents events = core.module(StrataEvents.class);
                    yield events == null ? "0" : String.valueOf(events.streakOf(id));
                }
                case "kills" -> stat(player, Statistic.PLAYER_KILLS);
                case "deaths" -> stat(player, Statistic.DEATHS);
                default -> null;
            };
        } catch (RuntimeException e) {
            value = null;
        }
        String cacheKey = id + ":" + key;
        if (value == null) {
            return last.getOrDefault(cacheKey, "0");
        }
        last.put(cacheKey, value);
        return value;
    }

    private String quests(UUID id, String key) {
        StrataMMO mmo = core.module(StrataMMO.class);
        QuestManager manager = mmo == null ? null : mmo.questManager();
        int[] summary = manager == null ? null : manager.summary(id);
        if (summary == null) {
            return key.equals("quests") ? "0/0" : "0";
        }
        return switch (key) {
            case "quests_done" -> String.valueOf(summary[0]);
            case "quests_total" -> String.valueOf(summary[1]);
            default -> summary[0] + "/" + summary[1];
        };
    }

    private static String stat(OfflinePlayer player, Statistic statistic) {
        Player online = player.getPlayer();
        return String.valueOf(online != null ? online.getStatistic(statistic) : player.getStatistic(statistic));
    }
}
