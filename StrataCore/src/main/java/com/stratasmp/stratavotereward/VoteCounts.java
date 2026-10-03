package com.stratasmp.stratavotereward;

import com.stratasmp.stratacore.StrataModule;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

public class VoteCounts {
   private final StrataModule plugin;
   private final File file;
   private final YamlConfiguration storage;

   public VoteCounts(StrataModule plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "vote_counts.yml");
      plugin.getDataFolder().mkdirs();
      this.storage = YamlConfiguration.loadConfiguration(this.file);
   }

   public synchronized int get(UUID id) {
      return this.storage.getInt(id.toString(), 0);
   }

   /** Adds to a player's lifetime vote total and returns the new total. */
   public synchronized int add(UUID id, int votes) {
      int total = this.storage.getInt(id.toString(), 0) + votes;
      this.storage.set(id.toString(), total);
      // votes are rare, so the file is written right here (atomically): no queued writes to reorder or lose at shutdown
      try {
         com.stratasmp.stratacore.AtomicYaml.save(this.storage, this.file);
      } catch (IOException e) {
         this.plugin.getLogger().warning("Couldn't save vote_counts.yml: " + e.getMessage());
      }
      return total;
   }
}
