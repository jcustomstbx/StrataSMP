package com.stratasmp.strataduels;

import java.io.File;
import java.io.IOException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import com.stratasmp.stratacore.StrataModule;

public class SeasonManager {
   private final StrataModule plugin;
   private final DataManager dataManager;
   private final File file;

   public SeasonManager(StrataModule plugin, DataManager dataManager) {
      this.plugin = plugin;
      this.dataManager = dataManager;
      this.file = new File(plugin.getDataFolder(), "season-history.yml");
   }

   public void start() {
      this.checkAndRollover();
      long intervalTicks = this.plugin.getConfig().getLong("season.check-interval-hours", 24L) * 60L * 60L * 20L;
      Bukkit.getScheduler().runTaskTimer(this.plugin, this::checkAndRollover, intervalTicks, intervalTicks);
   }

   private String currentSeasonKey() {
      return YearMonth.now(ZoneId.systemDefault()).toString();
   }

   private void checkAndRollover() {
      YamlConfiguration cfg = this.loadOrCreate();
      String storedKey = cfg.getString("current-season-key");
      String nowKey = this.currentSeasonKey();
      if (storedKey == null) {
         cfg.set("current-season-key", nowKey);
         this.save(cfg);
      } else if (!storedKey.equals(nowKey)) {
         this.rollover(cfg, storedKey);
      }
   }

   private void rollover(YamlConfiguration cfg, String endingSeasonKey) {
      this.dataManager.refreshLeaderboardCache();
      int topCount = this.plugin.getConfig().getInt("season.giveaway-top-count", 3);
      List<DuelPlayerData> topPlayers = this.dataManager.getTopN(topCount);
      String base = "seasons." + endingSeasonKey.replace('-', '_');
      cfg.set(base + ".ended-at", System.currentTimeMillis());
      int i = 0;

      for (DuelPlayerData data : topPlayers) {
         String entryBase = base + ".top." + i;
         cfg.set(entryBase + ".uuid", data.uuid.toString());
         cfg.set(entryBase + ".name", data.lastKnownName);
         cfg.set(entryBase + ".elo", data.seasonElo);
         cfg.set(entryBase + ".wins", data.seasonWins);
         cfg.set(entryBase + ".losses", data.seasonLosses);
         i++;
      }

      cfg.set("current-season-key", this.currentSeasonKey());
      this.save(cfg);
      this.announceRollover(topPlayers, endingSeasonKey);
      double startingElo = this.plugin.getConfig().getDouble("elo.starting-elo", 1000.0);
      this.dataManager.resetAllPlayersForNewSeason(startingElo);
      this.dataManager.refreshLeaderboardCache();
   }

   private void announceRollover(List<DuelPlayerData> topPlayers, String endingSeasonKey) {
      Bukkit.broadcast(
         Component.text("Duel season " + endingSeasonKey + " has ended! Top finishers (StrataSMP staff will hand out the Stratas giveaway):", NamedTextColor.GOLD)
      );
      int rank = 1;

      for (DuelPlayerData data : topPlayers) {
         Bukkit.broadcast(
            Component.text(
               "#" + rank + " " + data.lastKnownName + " - " + Math.round(data.seasonElo) + " ELO (" + data.seasonWins + "W/" + data.seasonLosses + "L)",
               NamedTextColor.YELLOW
            )
         );
         rank++;
      }

      Bukkit.broadcast(Component.text("Season ratings have been reset - good luck this season!", NamedTextColor.GRAY));
   }

   public List<String> lastSeasonSummaryLines() {
      YamlConfiguration cfg = this.loadOrCreate();
      ConfigurationSection seasons = cfg.getConfigurationSection("seasons");
      if (seasons != null && !seasons.getKeys(false).isEmpty()) {
         String lastKey = (String)seasons.getKeys(false).stream().sorted().reduce((a, b) -> b).orElse(null);
         if (lastKey == null) {
            return List.of("No season has ended yet.");
         } else {
            ConfigurationSection season = seasons.getConfigurationSection(lastKey);
            List<String> lines = new ArrayList<>();
            lines.add("Last season (" + lastKey.replace('_', '-') + ") top finishers:");
            ConfigurationSection top = season == null ? null : season.getConfigurationSection("top");
            if (top != null) {
               for (String key : top.getKeys(false).stream().sorted().toList()) {
                  ConfigurationSection entry = top.getConfigurationSection(key);
                  if (entry != null) {
                     lines.add(
                        "#"
                           + (Integer.parseInt(key) + 1)
                           + " "
                           + entry.getString("name")
                           + " - "
                           + Math.round(entry.getDouble("elo"))
                           + " ELO ("
                           + entry.getInt("wins")
                           + "W/"
                           + entry.getInt("losses")
                           + "L)"
                     );
                  }
               }
            }

            return lines;
         }
      } else {
         return List.of("No season has ended yet.");
      }
   }

   private YamlConfiguration loadOrCreate() {
      return !this.file.exists() ? new YamlConfiguration() : YamlConfiguration.loadConfiguration(this.file);
   }

   private void save(YamlConfiguration cfg) {
      try {
         cfg.save(this.file);
      } catch (IOException var3) {
         this.plugin.getLogger().log(Level.WARNING, "Couldn't save season-history.yml", (Throwable)var3);
      }
   }
}
