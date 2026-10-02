package com.stratasmp.strataduels.arena;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

public class ArenaResetManager {
   private static final int BLOCKS_PER_TICK = 1000;
   private static final long MIN_BASELINE_VOLUME = 80L;
   private static final int MIN_CAGE_BARRIERS = 100;
   private static final int MIN_HORIZONTAL_SPAN = 6;
   private final StrataModule plugin;
   private final File snapshotDir;

   public ArenaResetManager(StrataModule plugin) {
      this.plugin = plugin;
      this.snapshotDir = new File(plugin.getDataFolder(), "arena-snapshots");
   }

   public boolean hasBaseline(Arena arena) {
      return this.snapshotFile(arena).isFile();
   }

   public String captureBaseline(Arena arena) {
      if (!arena.hasBounds()) {
         return "Set both bounds first with /duelarena setbound1 and setbound2.";
      } else {
         World world = Bukkit.getWorld(arena.worldName);
         if (world == null) {
            return "Arena's world isn't loaded.";
         } else {
            Arena.Bounds b = arena.computeBounds();
            int spanX = b.maxX() - b.minX() + 1;
            int spanZ = b.maxZ() - b.minZ() + 1;
            if (spanX >= 6 && spanZ >= 6) {
               if (b.volume() < 80L) {
                  return "That bounding box is X:"
                     + spanX
                     + " Y:"
                     + (b.maxY() - b.minY() + 1)
                     + " Z:"
                     + spanZ
                     + " ("
                     + b.volume()
                     + " blocks) - too small to be the whole arena. Stand at two genuinely opposite corners of the room (diagonally across, not next to each other) and re-run setbound1/setbound2 before saving.";
               } else {
                  if (!this.snapshotDir.exists()) {
                     this.snapshotDir.mkdirs();
                  }

                  try {
                     try (BufferedWriter writer = new BufferedWriter(
                           new OutputStreamWriter(new FileOutputStream(this.snapshotFile(arena)), StandardCharsets.UTF_8)
                        )) {
                        for (int x = b.minX(); x <= b.maxX(); x++) {
                           for (int y = b.minY(); y <= b.maxY(); y++) {
                              for (int z = b.minZ(); z <= b.maxZ(); z++) {
                                 Block block = world.getBlockAt(x, y, z);
                                 writer.write(x + "," + y + "," + z + "," + block.getBlockData().getAsString());
                                 writer.newLine();
                              }
                           }
                        }
                     }

                     this.loadCages(List.of(arena));
                     return null;
                  } catch (IOException var131) {
                     this.plugin.getLogger().warning("Couldn't save arena baseline for " + arena.name + ": " + var131.getMessage());
                     return "Failed to save baseline - check console.";
                  }
               }
            } else {
               return "X:"
                  + spanX
                  + " Z:"
                  + spanZ
                  + " - one of those is basically a thin wall, not the room's footprint (both need to be at least 6 blocks). The two corners need to differ in BOTH X and Z, not just one - walk to the actual far corner of the floor, not just sideways along one wall, then re-run setbound1/setbound2.";
            }
         }
      }
   }

   /**
    * Works out, off the main thread, the box each arena's barrier walls enclose from its saved
    * baseline, so a duelist who clips out through a wall (elytra, ender pearl) is caught even
    * though the admin-set bounds run well past the walls. Arenas with no barriers keep using the bounds.
    */
   public void loadCages(Collection<Arena> arenas) {
      List<Arena> copy = List.copyOf(arenas);
      Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
         for (Arena arena : copy) {
            Arena.Bounds cage = this.cageFromBaseline(arena);
            arena.setCage(cage);
            if (cage != null) {
               this.plugin.getLogger().info("[ArenaReset] " + arena.name + " barrier cage x " + cage.minX() + ".." + cage.maxX()
                  + ", y " + cage.minY() + ".." + cage.maxY() + ", z " + cage.minZ() + ".." + cage.maxZ());
            }
         }
      });
   }

   private Arena.Bounds cageFromBaseline(Arena arena) {
      File file = this.snapshotFile(arena);
      if (!file.isFile()) {
         return null;
      }
      int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
      int barriers = 0;
      try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
         String line;
         while ((line = reader.readLine()) != null) {
            if (!line.contains(",minecraft:barrier")) {
               continue;
            }
            int first = line.indexOf(44);
            int second = line.indexOf(44, first + 1);
            int third = line.indexOf(44, second + 1);
            int x = Integer.parseInt(line.substring(0, first));
            int y = Integer.parseInt(line.substring(first + 1, second));
            int z = Integer.parseInt(line.substring(second + 1, third));
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
            barriers++;
         }
      } catch (IOException | RuntimeException e) {
         this.plugin.getLogger().warning("Couldn't read the barrier cage for " + arena.name + ": " + e.getMessage());
         return null;
      }
      if (barriers < MIN_CAGE_BARRIERS) {
         return null;
      }
      // a spawn outside the walls would mean the walls aren't the whole room, so never fence a spawn out
      if (arena.hasPos1) {
         minX = Math.min(minX, (int)Math.floor(arena.pos1X));
         maxX = Math.max(maxX, (int)Math.floor(arena.pos1X));
         minY = Math.min(minY, (int)Math.floor(arena.pos1Y));
         maxY = Math.max(maxY, (int)Math.floor(arena.pos1Y));
         minZ = Math.min(minZ, (int)Math.floor(arena.pos1Z));
         maxZ = Math.max(maxZ, (int)Math.floor(arena.pos1Z));
      }
      if (arena.hasPos2) {
         minX = Math.min(minX, (int)Math.floor(arena.pos2X));
         maxX = Math.max(maxX, (int)Math.floor(arena.pos2X));
         minY = Math.min(minY, (int)Math.floor(arena.pos2Y));
         maxY = Math.max(maxY, (int)Math.floor(arena.pos2Y));
         minZ = Math.min(minZ, (int)Math.floor(arena.pos2Z));
         maxZ = Math.max(maxZ, (int)Math.floor(arena.pos2Z));
      }
      for (double[] s : arena.spawns) {
         minX = Math.min(minX, (int)Math.floor(s[0]));
         maxX = Math.max(maxX, (int)Math.floor(s[0]));
         minY = Math.min(minY, (int)Math.floor(s[1]));
         maxY = Math.max(maxY, (int)Math.floor(s[1]));
         minZ = Math.min(minZ, (int)Math.floor(s[2]));
         maxZ = Math.max(maxZ, (int)Math.floor(s[2]));
      }
      return new Arena.Bounds(minX, maxX, minY, maxY, minZ, maxZ);
   }

   public int clearDroppedItems(Arena arena) {
      if (!arena.hasBounds()) {
         return 0;
      } else {
         World world = Bukkit.getWorld(arena.worldName);
         if (world == null) {
            return 0;
         } else {
            Arena.Bounds b = arena.computeBounds();
            BoundingBox box = new BoundingBox(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
            int removed = 0;

            for (Entity entity : world.getNearbyEntities(box)) {
               if (entity instanceof Item) {
                  entity.remove();
                  removed++;
               }
            }

            return removed;
         }
      }
   }

   public void resetArena(Arena arena) {
      this.resetArena(arena, () -> {
      });
   }

   /**
    * Restores the arena's saved baseline, then runs onComplete. Callers that reuse the arena for
    * a new match (MatchManager) must wait for onComplete before marking it free again - the
    * async block-restore below can take several ticks for a large arena, and freeing the arena
    * immediately let a new match start (and get matched a live opponent) while the previous
    * match's damage was still being reverted underneath them.
    */
   public void resetArena(final Arena arena, Runnable onComplete) {
      int itemsCleared = this.clearDroppedItems(arena);
      if (!this.hasBaseline(arena)) {
         this.plugin
            .getLogger()
            .info("[ArenaReset] " + arena.name + " has no saved baseline - only cleared " + itemsCleared + " dropped item(s), no blocks restored.");
         onComplete.run();
      } else {
         final World world = Bukkit.getWorld(arena.worldName);
         if (world == null) {
            onComplete.run();
            return;
         }

         final List<String> lines;
         try {
            lines = Files.readAllLines(this.snapshotFile(arena).toPath(), StandardCharsets.UTF_8);
         } catch (IOException var8) {
            this.plugin.getLogger().warning("Couldn't read arena baseline for " + arena.name + ": " + var8.getMessage());
            onComplete.run();
            return;
         }

         if (lines.isEmpty()) {
            onComplete.run();
            return;
         }

         int totalBlocks = lines.size();
         this.plugin
            .getLogger()
            .info(
               "[ArenaReset] " + arena.name + " reset starting - restoring " + totalBlocks + " block(s), cleared " + itemsCleared + " dropped item(s)."
            );
         if (this.plugin.isEnabled()) {
            (new BukkitRunnable() {
               int cursor = 0;

               public void run() {
                  for (int applied = 0; applied < 1000 && this.cursor < lines.size(); applied++) {
                     ArenaResetManager.this.applyLine(world, lines.get(this.cursor));
                     this.cursor++;
                  }

                  if (this.cursor >= lines.size()) {
                     this.cancel();
                     ArenaResetManager.this.plugin.getLogger().info("[ArenaReset] " + arena.name + " reset complete.");
                     onComplete.run();
                  }
               }
            }).runTaskTimer(this.plugin, 0L, 1L);
         } else {
            for (String line : lines) {
               this.applyLine(world, line);
            }

            this.plugin.getLogger().info("[ArenaReset] " + arena.name + " reset complete (synchronous, plugin disabling).");
            onComplete.run();
         }
      }
   }

   /** Restores the baseline in one go on the calling thread - for shutdown, when no scheduler tasks can be queued. */
   public void resetArenaBlocking(Arena arena) {
      this.clearDroppedItems(arena);
      World world = Bukkit.getWorld(arena.worldName);
      if (world == null || !this.hasBaseline(arena)) {
         return;
      }
      try {
         for (String line : Files.readAllLines(this.snapshotFile(arena).toPath(), StandardCharsets.UTF_8)) {
            this.applyLine(world, line);
         }
      } catch (IOException e) {
         this.plugin.getLogger().warning("Couldn't read arena baseline for " + arena.name + ": " + e.getMessage());
      }
   }

   private void applyLine(World world, String line) {
      int firstComma = line.indexOf(44);
      int secondComma = line.indexOf(44, firstComma + 1);
      int thirdComma = line.indexOf(44, secondComma + 1);
      if (thirdComma != -1) {
         try {
            int x = Integer.parseInt(line.substring(0, firstComma));
            int y = Integer.parseInt(line.substring(firstComma + 1, secondComma));
            int z = Integer.parseInt(line.substring(secondComma + 1, thirdComma));
            BlockData data = Bukkit.createBlockData(line.substring(thirdComma + 1));
            world.getBlockAt(x, y, z).setBlockData(data, false);
         } catch (Exception var10) {
         }
      }
   }

   private File snapshotFile(Arena arena) {
      return new File(this.snapshotDir, arena.name.toLowerCase() + ".snapshot");
   }
}
