package com.stratasmp.stratammo;

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
   private final String profile;
   private final File playerDataFolder;
   private final Map<UUID, PlayerData> online = new ConcurrentHashMap<>();
   private volatile List<PlayerData> leaderboardCache = Collections.emptyList();

   public DataManager(StrataModule plugin) {
      this(plugin, "smp");
   }

   public DataManager(StrataModule plugin, String profile) {
      this.plugin = plugin;
      this.profile = profile;
      File root = new File(plugin.getDataFolder(), "playerdata");
      this.playerDataFolder = "smp".equalsIgnoreCase(profile) ? root : new File(root, profile.toLowerCase(java.util.Locale.ROOT));
      if (!this.playerDataFolder.exists()) {
         this.playerDataFolder.mkdirs();
      }
   }

   public PlayerData load(Player player) {
      UUID id = player.getUniqueId();
      // already loaded (a world change inside the profile): keep the live copy, it has xp the disk doesn't
      PlayerData existing = this.online.get(id);
      if (existing != null) {
         existing.lastKnownName = player.getName();
         return existing;
      }
      PlayerData data = this.readFromDisk(id, player.getName());
      data.lastKnownName = player.getName();
      this.online.put(id, data);
      return data;
   }

   public PlayerData get(UUID uuid) {
      return this.online.get(uuid);
   }

   public void unload(Player player) {
      UUID id = player.getUniqueId();
      PlayerData data = this.online.remove(id);
      if (data != null) {
         this.save(data);
      }
   }

   public void saveAll() {
      for (PlayerData data : this.online.values()) {
         this.save(data);
      }
   }

   public void resetAll() {
      for (PlayerData data : this.online.values()) {
         for (Skill skill : Skill.values()) {
            data.setXp(skill, 0);
         }
      }

      File[] files = this.playerDataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
      if (files != null) {
         for (File file : files) {
            file.delete();
         }
      }

      this.refreshLeaderboardCache();
   }

   private File fileFor(UUID uuid) {
      return new File(this.playerDataFolder, uuid.toString() + ".yml");
   }

   private PlayerData readFromDisk(UUID uuid, String fallbackName) {
      File file = this.fileFor(uuid);
      if (!file.exists()) {
         return new PlayerData(uuid, fallbackName);
      } else {
         YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
         String name = yaml.getString("name", fallbackName);
         PlayerData data = new PlayerData(uuid, name);

         for (Skill skill : Skill.values()) {
            data.setXp(skill, yaml.getInt("xp." + skill.name().toLowerCase(), 0));
         }

         return data;
      }
   }

   private void save(PlayerData data) {
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.set("name", data.lastKnownName);

      for (Skill skill : Skill.values()) {
         yaml.set("xp." + skill.name().toLowerCase(), data.getXp(skill));
      }

      try {
         yaml.save(this.fileFor(data.uuid));
      } catch (IOException var7) {
         this.plugin.getLogger().log(Level.WARNING, "Couldn't save StrataMMO data for " + data.lastKnownName, (Throwable)var7);
      }
   }

   public void refreshLeaderboardCache() {
      File[] files = this.playerDataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
      List<PlayerData> result = new ArrayList<>();
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
         PlayerData onlineVersion = this.online.get(result.get(i).uuid);
         if (onlineVersion != null) {
            result.set(i, onlineVersion);
         }
      }

      for (PlayerData data : this.online.values()) {
         boolean alreadyIncluded = result.stream().anyMatch(d -> d.uuid.equals(data.uuid));
         if (!alreadyIncluded) {
            result.add(data);
         }
      }

      this.leaderboardCache = result;
   }

   public List<PlayerData> getLeaderboardSnapshot() {
      return this.leaderboardCache;
   }

   public PlayerData findByName(String name) {
      for (PlayerData data : this.online.values()) {
         if (data.lastKnownName.equalsIgnoreCase(name)) {
            return data;
         }
      }

      for (PlayerData datax : this.leaderboardCache) {
         if (datax.lastKnownName.equalsIgnoreCase(name)) {
            return datax;
         }
      }

      return null;
   }
}
