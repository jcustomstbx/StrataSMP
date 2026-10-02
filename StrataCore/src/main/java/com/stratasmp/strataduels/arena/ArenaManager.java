package com.stratasmp.strataduels.arena;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import com.stratasmp.stratacore.StrataModule;

public class ArenaManager {
   private final StrataModule plugin;
   private final File file;
   private final Map<String, Arena> arenas = new LinkedHashMap<>();

   public ArenaManager(StrataModule plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "arenas.yml");
      this.load();
   }

   private void load() {
      if (this.file.exists()) {
         YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.file);
         ConfigurationSection section = cfg.getConfigurationSection("arenas");
         if (section != null) {
            for (String name : section.getKeys(false)) {
               ConfigurationSection a = section.getConfigurationSection(name);
               if (a != null) {
                  Arena arena = new Arena(name);
                  arena.worldName = a.getString("world");
                  arena.enabled = a.getBoolean("enabled", true);
                  arena.ffa = a.getBoolean("ffa", false);
                  ConfigurationSection spawnSection = a.getConfigurationSection("spawns");
                  if (spawnSection != null) {
                     for (String index : spawnSection.getKeys(false)) {
                        ConfigurationSection s = spawnSection.getConfigurationSection(index);
                        if (s != null) {
                           arena.spawns.add(new double[]{s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), s.getDouble("yaw"), s.getDouble("pitch")});
                        }
                     }
                  }
                  ConfigurationSection p1 = a.getConfigurationSection("pos1");
                  if (p1 != null) {
                     arena.pos1X = p1.getDouble("x");
                     arena.pos1Y = p1.getDouble("y");
                     arena.pos1Z = p1.getDouble("z");
                     arena.pos1Yaw = (float)p1.getDouble("yaw");
                     arena.pos1Pitch = (float)p1.getDouble("pitch");
                     arena.hasPos1 = true;
                  }

                  ConfigurationSection p2 = a.getConfigurationSection("pos2");
                  if (p2 != null) {
                     arena.pos2X = p2.getDouble("x");
                     arena.pos2Y = p2.getDouble("y");
                     arena.pos2Z = p2.getDouble("z");
                     arena.pos2Yaw = (float)p2.getDouble("yaw");
                     arena.pos2Pitch = (float)p2.getDouble("pitch");
                     arena.hasPos2 = true;
                  }

                  ConfigurationSection b1 = a.getConfigurationSection("bound1");
                  if (b1 != null) {
                     arena.boundX1 = b1.getDouble("x");
                     arena.boundY1 = b1.getDouble("y");
                     arena.boundZ1 = b1.getDouble("z");
                     arena.hasBound1 = true;
                  }

                  ConfigurationSection b2 = a.getConfigurationSection("bound2");
                  if (b2 != null) {
                     arena.boundX2 = b2.getDouble("x");
                     arena.boundY2 = b2.getDouble("y");
                     arena.boundZ2 = b2.getDouble("z");
                     arena.hasBound2 = true;
                  }

                  this.arenas.put(name.toLowerCase(), arena);
               }
            }
         }
      }
   }

   public void save() {
      YamlConfiguration cfg = new YamlConfiguration();

      for (Arena arena : this.arenas.values()) {
         String base = "arenas." + arena.name;
         cfg.set(base + ".world", arena.worldName);
         cfg.set(base + ".enabled", arena.enabled);
         cfg.set(base + ".ffa", arena.ffa);
         for (int i = 0; i < arena.spawns.size(); i++) {
            double[] s = arena.spawns.get(i);
            cfg.set(base + ".spawns." + i + ".x", s[0]);
            cfg.set(base + ".spawns." + i + ".y", s[1]);
            cfg.set(base + ".spawns." + i + ".z", s[2]);
            cfg.set(base + ".spawns." + i + ".yaw", s[3]);
            cfg.set(base + ".spawns." + i + ".pitch", s[4]);
         }
         if (arena.hasPos1) {
            cfg.set(base + ".pos1.x", arena.pos1X);
            cfg.set(base + ".pos1.y", arena.pos1Y);
            cfg.set(base + ".pos1.z", arena.pos1Z);
            cfg.set(base + ".pos1.yaw", (double)arena.pos1Yaw);
            cfg.set(base + ".pos1.pitch", (double)arena.pos1Pitch);
         }

         if (arena.hasPos2) {
            cfg.set(base + ".pos2.x", arena.pos2X);
            cfg.set(base + ".pos2.y", arena.pos2Y);
            cfg.set(base + ".pos2.z", arena.pos2Z);
            cfg.set(base + ".pos2.yaw", (double)arena.pos2Yaw);
            cfg.set(base + ".pos2.pitch", (double)arena.pos2Pitch);
         }

         if (arena.hasBound1) {
            cfg.set(base + ".bound1.x", arena.boundX1);
            cfg.set(base + ".bound1.y", arena.boundY1);
            cfg.set(base + ".bound1.z", arena.boundZ1);
         }

         if (arena.hasBound2) {
            cfg.set(base + ".bound2.x", arena.boundX2);
            cfg.set(base + ".bound2.y", arena.boundY2);
            cfg.set(base + ".bound2.z", arena.boundZ2);
         }
      }

      try {
         cfg.save(this.file);
      } catch (IOException var5) {
         this.plugin.getLogger().warning("Couldn't save arenas.yml: " + var5.getMessage());
      }
   }

   public String createArena(String name) {
      String key = name.toLowerCase();
      if (this.arenas.containsKey(key)) {
         return "An arena named " + name + " already exists.";
      } else {
         this.arenas.put(key, new Arena(name));
         this.save();
         return null;
      }
   }

   public String setPos(String name, int posNum, Player admin) {
      Arena arena = this.arenas.get(name.toLowerCase());
      if (arena == null) {
         return "No arena named " + name + ".";
      } else {
         arena.worldName = admin.getWorld().getName();
         Location loc = admin.getLocation();
         if (posNum == 1) {
            arena.pos1X = loc.getX();
            arena.pos1Y = loc.getY();
            arena.pos1Z = loc.getZ();
            arena.pos1Yaw = loc.getYaw();
            arena.pos1Pitch = loc.getPitch();
            arena.hasPos1 = true;
         } else {
            arena.pos2X = loc.getX();
            arena.pos2Y = loc.getY();
            arena.pos2Z = loc.getZ();
            arena.pos2Yaw = loc.getYaw();
            arena.pos2Pitch = loc.getPitch();
            arena.hasPos2 = true;
         }

         this.save();
         return null;
      }
   }

   public String setBound(String name, int cornerNum, Player admin) {
      Arena arena = this.arenas.get(name.toLowerCase());
      if (arena == null) {
         return "No arena named " + name + ".";
      } else {
         Location loc = admin.getLocation();
         if (cornerNum == 1) {
            arena.boundX1 = loc.getX();
            arena.boundY1 = loc.getY();
            arena.boundZ1 = loc.getZ();
            arena.hasBound1 = true;
         } else {
            arena.boundX2 = loc.getX();
            arena.boundY2 = loc.getY();
            arena.boundZ2 = loc.getZ();
            arena.hasBound2 = true;
         }

         this.save();
         return null;
      }
   }

   public String deleteArena(String name) {
      Arena removed = this.arenas.remove(name.toLowerCase());
      if (removed == null) {
         return "No arena named " + name + ".";
      } else {
         this.save();
         return null;
      }
   }

   public String toggleEnabled(String name) {
      Arena arena = this.arenas.get(name.toLowerCase());
      if (arena == null) {
         return "No arena named " + name + ".";
      } else {
         arena.enabled = !arena.enabled;
         this.save();
         return null;
      }
   }

   public String toggleFfa(String name) {
      Arena arena = this.arenas.get(name.toLowerCase());
      if (arena == null) {
         return "No arena named " + name + ".";
      }
      arena.ffa = !arena.ffa;
      this.save();
      return null;
   }

   public String addSpawn(String name, Player admin) {
      Arena arena = this.arenas.get(name.toLowerCase());
      if (arena == null) {
         return "No arena named " + name + ".";
      }
      if (arena.worldName != null && !arena.worldName.equals(admin.getWorld().getName())) {
         return "You're not in " + arena.name + "'s world (" + arena.worldName + ").";
      }
      Location loc = admin.getLocation();
      arena.worldName = admin.getWorld().getName();
      arena.spawns.add(new double[]{loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()});
      this.save();
      return null;
   }

   public String clearSpawns(String name) {
      Arena arena = this.arenas.get(name.toLowerCase());
      if (arena == null) {
         return "No arena named " + name + ".";
      }
      arena.spawns.clear();
      this.save();
      return null;
   }

   public List<Arena> list() {
      return new ArrayList<>(this.arenas.values());
   }

   public Arena get(String name) {
      return this.arenas.get(name.toLowerCase());
   }

   public Optional<Arena> findFreeArena() {
      return this.arenas.values().stream().filter(a -> a.enabled && !a.ffa && !a.inUse && a.isReady()).findAny();
   }

   public void markInUse(Arena arena, boolean inUse) {
      arena.inUse = inUse;
   }
}
