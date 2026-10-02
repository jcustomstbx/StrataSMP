package com.stratasmp.strataweapons;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.YamlConfiguration;
import com.stratasmp.stratacore.StrataModule;

public class KillMessageManager {
   private static final Pattern FORMAT_CODES = Pattern.compile("[&§][0-9a-fk-orA-FK-OR]");
   private final StrataModule plugin;
   private final File file;
   private final YamlConfiguration storage;
   private final Map<UUID, String> messages = new HashMap<>();

   public KillMessageManager(StrataModule plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "killmessages.yml");
      this.storage = this.loadFile();

      for (String key : this.storage.getKeys(false)) {
         try {
            this.messages.put(UUID.fromString(key), this.storage.getString(key));
         } catch (IllegalArgumentException var5) {
         }
      }
   }

   private YamlConfiguration loadFile() {
      if (!this.file.exists()) {
         this.plugin.getDataFolder().mkdirs();
      }

      return YamlConfiguration.loadConfiguration(this.file);
   }

   public String defaultTemplate() {
      return this.plugin.getConfig().getString("kill-messages.default-format", "{victim} was struck down by {killer}'s {weapon}!");
   }

   public int maxLength() {
      return this.plugin.getConfig().getInt("kill-messages.max-length", 80);
   }

   public String templateFor(UUID playerId) {
      return this.messages.getOrDefault(playerId, this.defaultTemplate());
   }

   public boolean hasCustom(UUID playerId) {
      return this.messages.containsKey(playerId);
   }

   public String validate(String template) {
      if (template != null && !template.isBlank()) {
         if (template.length() > this.maxLength()) {
            return "Message is too long (max " + this.maxLength() + " characters).";
         } else if (template.contains("{killer}") && template.contains("{victim}")) {
            if (FORMAT_CODES.matcher(template).find()) {
               return "Color/formatting codes aren't allowed in a custom message.";
            } else {
               String lower = template.toLowerCase();

               for (String banned : this.blacklist()) {
                  if (!banned.isBlank() && lower.contains(banned.toLowerCase())) {
                     return "That message isn't allowed on this server.";
                  }
               }

               return null;
            }
         } else {
            return "Message must contain both {killer} and {victim}.";
         }
      } else {
         return "Message can't be empty.";
      }
   }

   private List<String> blacklist() {
      return this.plugin.getConfig().getStringList("kill-messages.blacklisted-words");
   }

   public void set(UUID playerId, String template) {
      this.messages.put(playerId, template);
      this.storage.set(playerId.toString(), template);
      this.save();
   }

   public void reset(UUID playerId) {
      this.messages.remove(playerId);
      this.storage.set(playerId.toString(), null);
      this.save();
   }

   private void save() {
      try {
         this.storage.save(this.file);
      } catch (IOException var2) {
         this.plugin.getLogger().warning("Could not save killmessages.yml: " + var2.getMessage());
      }
   }
}
