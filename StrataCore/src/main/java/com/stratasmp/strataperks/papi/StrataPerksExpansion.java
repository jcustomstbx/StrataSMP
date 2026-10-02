package com.stratasmp.strataperks.papi;

import com.stratasmp.strataperks.StrataPerks;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/** %strataperks_balance% - a player's StrataPerks balance. */
public final class StrataPerksExpansion extends PlaceholderExpansion {

    private final StrataPerks plugin;

    public StrataPerksExpansion(StrataPerks plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "strataperks"; }
    @Override public @NotNull String getAuthor() { return "JCustoms"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        long balance = plugin.strataperks().getBalance(player.getUniqueId());
        return switch (params.toLowerCase()) {
            case "balance" -> String.valueOf(balance);
            default -> null;
        };
    }
}
