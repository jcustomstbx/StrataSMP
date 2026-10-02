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
        long bal = plugin.stratas().getBalance(player.getUniqueId());
        return switch (params.toLowerCase()) {
            case "balance", "balance_raw" -> String.valueOf(bal);
            case "balance_formatted" -> plugin.money(bal);
            case "balance_short" -> Amounts.formatShort(bal);
            case "rank" -> String.valueOf(rankOf(player));
            default -> null;
        };
    }

    private int rankOf(OfflinePlayer player) {
        var top = plugin.stratas().top(1000);
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).getKey().equals(player.getUniqueId())) {
                return i + 1;
            }
        }
        return 0;
    }
}
