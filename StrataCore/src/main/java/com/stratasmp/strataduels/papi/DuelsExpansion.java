package com.stratasmp.strataduels.papi;

import com.stratasmp.strataduels.DataManager;
import com.stratasmp.strataduels.DuelPlayerData;
import com.stratasmp.strataduels.StrataDuels;
import com.stratasmp.strataduels.RankTier;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/** %strataduels_elo%, %strataduels_tier%, %strataduels_rank%, %strataduels_wins%, %strataduels_losses%, %strataduels_winstreak% */
public final class DuelsExpansion extends PlaceholderExpansion {

    private final StrataDuels plugin;
    private final DataManager dataManager;

    public DuelsExpansion(StrataDuels plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    @Override public @NotNull String getIdentifier() { return "strataduels"; }
    @Override public @NotNull String getAuthor() { return "JCustoms"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        DuelPlayerData data = dataManager.getOrLoad(player.getUniqueId(), player.getName() == null ? "Unknown" : player.getName());
        return switch (params.toLowerCase()) {
            case "elo" -> String.valueOf(Math.round(data.seasonElo));
            case "tier" -> tierOf(data).displayName();
            case "rank" -> String.valueOf(rankOf(player));
            case "wins" -> String.valueOf(data.seasonWins);
            case "losses" -> String.valueOf(data.seasonLosses);
            case "winstreak" -> String.valueOf(data.seasonWinStreak);
            case "games_played" -> String.valueOf(data.seasonGamesPlayed);
            default -> null;
        };
    }

    private RankTier tierOf(DuelPlayerData data) {
        var thresholds = plugin.getConfig().getConfigurationSection("rank-tiers");
        return RankTier.forElo(data.seasonElo, thresholds);
    }

    private int rankOf(OfflinePlayer player) {
        var snapshot = dataManager.getLeaderboardSnapshot();
        for (int i = 0; i < snapshot.size(); i++) {
            if (snapshot.get(i).uuid.equals(player.getUniqueId())) {
                return i + 1;
            }
        }
        return 0;
    }
}
