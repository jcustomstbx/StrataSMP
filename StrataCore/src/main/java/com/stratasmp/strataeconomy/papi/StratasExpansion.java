package com.stratasmp.strataeconomy.papi;

import com.stratasmp.strataeconomy.StrataEconomy;
import com.stratasmp.strataeconomy.currency.Amounts;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * Keeps the identifier "stratas" so existing %stratas_balance% placeholders in
 * TAB / DiscordSRV / scoreboards keep working after the StratasPlugin swap.
 */
public final class StratasExpansion extends PlaceholderExpansion {

    private final StrataEconomy plugin;

    public StratasExpansion(StrataEconomy plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "stratas"; }
    @Override public @NotNull String getAuthor() { return "JCustoms"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public @NotNull String getRequiredPlugin() { return "StrataCore"; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        // placeholders run on the main thread: never touch the database from here
        Long cached = plugin.stratas().cachedBalance(player.getUniqueId());
        // one lookup per player at a time, so a database outage doesn't pile up a task per scoreboard refresh
        if (cached == null && loading.add(player.getUniqueId())) {
            plugin.async(() -> {
                try {
                    plugin.stratas().preload(player.getUniqueId(), null);
                } finally {
                    loading.remove(player.getUniqueId());
                }
            });
        }
        long bal = cached == null ? 0L : cached;
        return switch (params.toLowerCase()) {
            case "balance", "balance_raw" -> String.valueOf(bal);
            case "balance_formatted" -> plugin.money(bal);
            case "balance_short" -> Amounts.formatShort(bal);
            case "rank" -> String.valueOf(rankOf(player));
            default -> null;
        };
    }

    private final java.util.Set<java.util.UUID> loading = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile java.util.List<java.util.Map.Entry<java.util.UUID, Long>> topCache = java.util.List.of();
    private volatile long topCachedAt;
    private final java.util.concurrent.atomic.AtomicBoolean refreshing = new java.util.concurrent.atomic.AtomicBoolean();

    private int rankOf(OfflinePlayer player) {
        if (System.currentTimeMillis() - topCachedAt > 60_000L && refreshing.compareAndSet(false, true)) {
            plugin.async(() -> {
                try {
                    topCache = plugin.stratas().top(1000);
                    topCachedAt = System.currentTimeMillis();
                } finally {
                    refreshing.set(false);
                }
            });
        }
        var top = topCache;
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).getKey().equals(player.getUniqueId())) {
                return i + 1;
            }
        }
        return 0;
    }
}
