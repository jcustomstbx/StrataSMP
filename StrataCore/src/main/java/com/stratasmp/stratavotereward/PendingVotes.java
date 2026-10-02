package com.stratasmp.stratavotereward;

import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.file.YamlConfiguration;
import com.stratasmp.stratacore.StrataModule;

public class PendingVotes {
   private final StrataModule plugin;
   private final File file;
   private final YamlConfiguration storage;

   public PendingVotes(StrataModule plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "pending_votes.yml");
      if (!this.file.exists()) {
         plugin.getDataFolder().mkdirs();
      }

      this.storage = YamlConfiguration.loadConfiguration(this.file);
   }

   public void add(String username, int amount) {
      if (amount <= 0) {
         return;
      }
      String key = username.toLowerCase();
      int existing = this.storage.getInt(key, 0);
      this.storage.set(key, existing + amount);
      this.save();
   }

   public int takePending(String username) {
      String key = username.toLowerCase();
      int amount = this.storage.getInt(key, 0);
      if (amount > 0) {
         this.storage.set(key, null);
         this.save();
      }

      return amount;
   }

   /** Namespaced under "keys." so it can't collide with a username stored at the top level for stratas. */
   public void addKeys(String username, int amount) {
      if (amount <= 0) {
         return;
      }
      String key = "keys." + username.toLowerCase();
      int existing = this.storage.getInt(key, 0);
      this.storage.set(key, existing + amount);
      this.save();
   }

   public int takePendingKeys(String username) {
      String key = "keys." + username.toLowerCase();
      int amount = this.storage.getInt(key, 0);
      if (amount > 0) {
         this.storage.set(key, null);
         this.save();
      }

      return amount;
   }

   private void save() {
      try {
         this.storage.save(this.file);
      } catch (IOException var2) {
         this.plugin.getLogger().warning("Could not save pending_votes.yml: " + var2.getMessage());
      }
   }
}
