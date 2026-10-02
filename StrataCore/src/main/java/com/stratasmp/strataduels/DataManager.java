package com.stratasmp.strataduels;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import com.stratasmp.stratacore.StrataModule;

public class DataManager {
   private final StrataModule plugin;
   private final File playerDataFolder;
   private final double startingElo;
   private final Map<UUID, DuelPlayerData> online = new ConcurrentHashMap<>();
   private volatile List<DuelPlayerData> leaderboardCache = Collections.emptyList();

   public DataManager(StrataModule plugin, double startingElo) {
      this.plugin = plugin;
      this.startingElo = startingElo;
      this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
      if (!this.playerDataFolder.exists()) {
         this.playerDataFolder.mkdirs();
      }
   }

   public DuelPlayerData load(Player player) {
      UUID id = player.getUniqueId();
      DuelPlayerData data = this.readFromDisk(id, player.getName());
      data.lastKnownName = player.getName();
      this.online.put(id, data);
      return data;
   }

   public DuelPlayerData get(UUID uuid) {
      return this.online.get(uuid);
   }

   /** Same as {@link #get(UUID)} but falls back to disk for an offline player instead of returning null. */
   public DuelPlayerData getOrLoad(UUID uuid, String fallbackName) {
      DuelPlayerData cached = this.online.get(uuid);
      return cached != null ? cached : this.readFromDisk(uuid, fallbackName);
   }

   public void unload(Player player) {
      UUID id = player.getUniqueId();
      DuelPlayerData data = this.online.remove(id);
      if (data != null) {
         this.save(data);
      }
   }

   public void saveAll() {
      for (DuelPlayerData data : this.online.values()) {
         this.save(data);
      }
   }

   private File fileFor(UUID uuid) {
      return new File(this.playerDataFolder, uuid.toString() + ".yml");
   }

   private DuelPlayerData readFromDisk(UUID uuid, String fallbackName) {
      File file = this.fileFor(uuid);
      if (!file.exists()) {
         return new DuelPlayerData(uuid, fallbackName, this.startingElo);
      } else {
         YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
         String name = yaml.getString("name", fallbackName);
         DuelPlayerData data = new DuelPlayerData(uuid, name, this.startingElo);
         data.seasonElo = yaml.getDouble("season.elo", this.startingElo);
         data.seasonWins = yaml.getInt("season.wins", 0);
         data.seasonLosses = yaml.getInt("season.losses", 0);
         data.seasonWinStreak = yaml.getInt("season.win-streak", 0);
         data.seasonBestStreak = yaml.getInt("season.best-streak", 0);
         data.seasonGamesPlayed = yaml.getInt("season.games-played", 0);
         data.lifetimeWins = yaml.getInt("lifetime.wins", 0);
         data.lifetimeLosses = yaml.getInt("lifetime.losses", 0);
         data.lifetimeGamesPlayed = yaml.getInt("lifetime.games-played", 0);
         return data;
      }
   }

   private void save(DuelPlayerData data) {
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.set("name", data.lastKnownName);
      yaml.set("season.elo", data.seasonElo);
      yaml.set("season.wins", data.seasonWins);
      yaml.set("season.losses", data.seasonLosses);
      yaml.set("season.win-streak", data.seasonWinStreak);
      yaml.set("season.best-streak", data.seasonBestStreak);
      yaml.set("season.games-played", data.seasonGamesPlayed);
      yaml.set("lifetime.wins", data.lifetimeWins);
      yaml.set("lifetime.losses", data.lifetimeLosses);
      yaml.set("lifetime.games-played", data.lifetimeGamesPlayed);

      try {
         yaml.save(this.fileFor(data.uuid));
      } catch (IOException var4) {
         this.plugin.getLogger().log(Level.WARNING, "Couldn't save StrataDuels data for " + data.lastKnownName, (Throwable)var4);
      }
   }

   public void refreshLeaderboardCache() {
      File[] files = this.playerDataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
      List<DuelPlayerData> result = new ArrayList<>();
      if (files != null) {
         for (File file : files) {
            String idPart = file.getName().substring(0, file.getName().length() - 4);

            try {
               UUID id = UUID.fromString(idPart);
               result.add(this.readFromDisk(id, "Unknown"));
            } catch (IllegalArgumentException var9) {
            }
         }
      }

      for (int i = 0; i < result.size(); i++) {
         DuelPlayerData onlineVersion = this.online.get(result.get(i).uuid);
         if (onlineVersion != null) {
            result.set(i, onlineVersion);
         }
      }

      for (DuelPlayerData data : this.online.values()) {
         boolean alreadyIncluded = result.stream().anyMatch(d -> d.uuid.equals(data.uuid));
         if (!alreadyIncluded) {
            result.add(data);
         }
      }

      result.sort((a, b) -> Double.compare(b.seasonElo, a.seasonElo));
      this.leaderboardCache = result;
   }

   public List<DuelPlayerData> getLeaderboardSnapshot() {
      return this.leaderboardCache;
   }

   public List<DuelPlayerData> getTopN(int n) {
      List<DuelPlayerData> snapshot = this.leaderboardCache;
      return snapshot.subList(0, Math.min(n, snapshot.size()));
   }

   public DuelPlayerData findByName(String name) {
      for (DuelPlayerData data : this.online.values()) {
         if (data.lastKnownName.equalsIgnoreCase(name)) {
            return data;
         }
      }

      for (DuelPlayerData datax : this.leaderboardCache) {
         if (datax.lastKnownName.equalsIgnoreCase(name)) {
            return datax;
         }
      }

      return null;
   }

   public void resetAllPlayersForNewSeason(double startingElo) {
      File[] files = this.playerDataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
      if (files != null) {
         for (File file : files) {
            String idPart = file.getName().substring(0, file.getName().length() - 4);

            UUID id;
            try {
               id = UUID.fromString(idPart);
            } catch (IllegalArgumentException var12) {
               continue;
            }

            DuelPlayerData onlineVersion = this.online.get(id);
            if (onlineVersion != null) {
               onlineVersion.resetSeason(startingElo);
               this.save(onlineVersion);
            } else {
               DuelPlayerData data = this.readFromDisk(id, "Unknown");
               data.resetSeason(startingElo);
               this.save(data);
            }
         }
      }
   }
}
