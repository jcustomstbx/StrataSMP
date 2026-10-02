package com.stratasmp.strataduels;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import com.stratasmp.stratacore.StrataModule;

public class WebPusher {
   private final StrataModule plugin;
   private final DataManager dataManager;
   private final String apiUrl;
   private final String apiKey;
   private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).build();

   public WebPusher(StrataModule plugin, DataManager dataManager, String apiUrl, String apiKey) {
      this.plugin = plugin;
      this.dataManager = dataManager;
      this.apiUrl = apiUrl;
      this.apiKey = apiKey;
   }

   public void push() {
      List<DuelPlayerData> snapshot = this.dataManager.getLeaderboardSnapshot();
      if (!snapshot.isEmpty()) {
         ConfigurationSection thresholds = this.plugin.getConfig().getConfigurationSection("rank-tiers");
         HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(this.apiUrl))
            .header("Content-Type", "application/json")
            .header("X-Api-Key", this.apiKey)
            .timeout(Duration.ofSeconds(10L))
            .POST(BodyPublishers.ofString(this.buildPayload(snapshot, thresholds)))
            .build();
         this.client.sendAsync(request, BodyHandlers.ofString()).whenComplete((response, error) -> {
            if (error != null) {
               this.plugin.getLogger().log(Level.WARNING, "StrataDuels leaderboard push failed", error);
            } else if (response.statusCode() != 200) {
               this.plugin.getLogger().warning("StrataDuels leaderboard push rejected: HTTP " + response.statusCode() + " - " + response.body());
            } else {
               this.plugin.getLogger().info("StrataDuels leaderboard push OK (" + snapshot.size() + " player(s)).");
            }
         });
      }
   }

   private String buildPayload(List<DuelPlayerData> snapshot, ConfigurationSection thresholds) {
      StringBuilder json = new StringBuilder();
      json.append("{\"players\":[");
      boolean first = true;

      for (DuelPlayerData data : snapshot) {
         if (!first) {
            json.append(',');
         }

         first = false;
         RankTier tier = RankTier.forElo(data.seasonElo, thresholds);
         json.append("{\"uuid\":\"").append(data.uuid).append("\",");
         json.append("\"username\":\"").append(this.escape(data.lastKnownName)).append("\",");
         json.append("\"season_elo\":").append(data.seasonElo).append(',');
         json.append("\"tier\":\"").append(tier.name().toLowerCase()).append("\",");
         json.append("\"wins\":").append(data.seasonWins).append(',');
         json.append("\"losses\":").append(data.seasonLosses).append(',');
         json.append("\"win_streak\":").append(data.seasonWinStreak).append(',');
         json.append("\"games_played\":").append(data.seasonGamesPlayed);
         json.append('}');
      }

      json.append("]}");
      return json.toString();
   }

   private String escape(String value) {
      return value.replace("\\", "\\\\").replace("\"", "\\\"");
   }
}
