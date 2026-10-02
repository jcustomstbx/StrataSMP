package com.stratasmp.stratammo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import com.stratasmp.stratacore.StrataModule;

public class LeaderboardPusher {
   private final StrataModule plugin;
   private final DataManager dataManager;
   private final LevelCurve levelCurve;
   private final String apiUrl;
   private final String apiKey;
   private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).build();

   public LeaderboardPusher(StrataModule plugin, DataManager dataManager, LevelCurve levelCurve, String apiUrl, String apiKey) {
      this.plugin = plugin;
      this.dataManager = dataManager;
      this.levelCurve = levelCurve;
      this.apiUrl = apiUrl;
      this.apiKey = apiKey;
   }

   public void push() {
      List<PlayerData> snapshot = this.dataManager.getLeaderboardSnapshot();
      if (!snapshot.isEmpty()) {
         HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(this.apiUrl))
            .header("Content-Type", "application/json")
            .header("X-Api-Key", this.apiKey)
            .timeout(Duration.ofSeconds(10L))
            .POST(BodyPublishers.ofString(this.buildPayload(snapshot)))
            .build();
         this.client.sendAsync(request, BodyHandlers.ofString()).whenComplete((response, error) -> {
            if (error != null) {
               this.plugin.getLogger().log(Level.WARNING, "StrataMMO leaderboard push failed", error);
            } else if (response.statusCode() != 200) {
               this.plugin.getLogger().warning("StrataMMO leaderboard push rejected: HTTP " + response.statusCode() + " - " + response.body());
            } else {
               this.plugin.getLogger().info("StrataMMO leaderboard push OK (" + snapshot.size() + " player(s)).");
            }
         });
      }
   }

   private String buildPayload(List<PlayerData> snapshot) {
      StringBuilder json = new StringBuilder();
      // every push already sends the complete roster (readFromDisk over all of
      // plugins/StrataMMO/playerdata/*.yml, not just online/changed players), so
      // the site can treat every push as authoritative and self-correcting.
      json.append("{\"full_sync\":true,\"players\":[");
      boolean firstPlayer = true;

      for (PlayerData data : snapshot) {
         if (!firstPlayer) {
            json.append(',');
         }

         firstPlayer = false;
         json.append("{\"uuid\":\"").append(data.uuid).append("\",");
         json.append("\"username\":\"").append(this.escape(data.lastKnownName)).append("\",");
         json.append("\"skills\":{");
         boolean firstSkill = true;

         for (Skill skill : Skill.values()) {
            if (!firstSkill) {
               json.append(',');
            }

            firstSkill = false;
            int xp = data.getXp(skill);
            int[] levelProgress = this.levelCurve.levelFromTotalXp(xp);
            int level = levelProgress[0];
            int intoLevel = levelProgress[1];
            int forNextLevel = this.levelCurve.xpForLevel(level);
            int progressPercent = forNextLevel <= 0 ? 0 : Math.min(100, intoLevel * 100 / forNextLevel);
            json.append('"')
               .append(skill.name().toLowerCase())
               .append("\":{\"level\":")
               .append(level)
               .append(",\"xp\":")
               .append(xp)
               .append(",\"progress\":")
               .append(progressPercent)
               .append('}');
         }

         json.append("}}");
      }

      json.append("]}");
      return json.toString();
   }

   private String escape(String value) {
      return value.replace("\\", "\\\\").replace("\"", "\\\"");
   }
}
