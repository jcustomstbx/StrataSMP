package com.stratasmp.strataranks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Consistent rank-selected name colour for TAB and chat formats. */
final class RankPlaceholders extends PlaceholderExpansion {
    private final StrataRanks ranks;

    RankPlaceholders(StrataRanks ranks) { this.ranks = ranks; }

    @Override public @NotNull String getIdentifier() { return "strataranks"; }
    @Override public @NotNull String getAuthor() { return "JCustoms"; }
    @Override public @NotNull String getVersion() { return "1.0.0"; }
    @Override public boolean persist() { return true; }

    @Override public String onRequest(OfflinePlayer offline, @NotNull String params) {
        Player player = offline.getPlayer();
        if (player == null) return "";
        String color = ranks.nameColor(player);
        String display = org.bukkit.ChatColor.stripColor(player.getDisplayName());
        if (display == null || display.isBlank()) display = player.getName();
        String name = display;
        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "name" -> color.isEmpty() ? name : "&#" + color.substring(1) + name;
            case "namecolor" -> color.isEmpty() ? "&f" : "&#" + color.substring(1);
            default -> null;
        };
    }
}
