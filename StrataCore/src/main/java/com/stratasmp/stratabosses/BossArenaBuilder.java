package com.stratasmp.stratabosses;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.HeightMap;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import com.stratasmp.stratacore.StrataModule;

public class BossArenaBuilder {
   private static final int RADIUS = 9;
   private static final int CLEARANCE_HEIGHT = 6;
   private static final Map<String, BossArenaBuilder.Theme> THEMES = Map.of(
      "zekka",
      new BossArenaBuilder.Theme(Material.POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BLACKSTONE_BRICK_WALL, Material.GILDED_BLACKSTONE, 5),
      "vaelspire",
      new BossArenaBuilder.Theme(Material.NETHER_BRICKS, Material.NETHER_BRICK_FENCE, Material.SOUL_LANTERN, 6),
      "thornmaw",
      new BossArenaBuilder.Theme(Material.COARSE_DIRT, Material.STRIPPED_DARK_OAK_LOG, Material.BONE_BLOCK, 8),
      "abyssal_coilfang",
      new BossArenaBuilder.Theme(Material.PRISMARINE, Material.DARK_PRISMARINE, Material.SEA_LANTERN, 6),
      "cinderjaw",
      new BossArenaBuilder.Theme(Material.RED_NETHER_BRICKS, Material.BASALT, Material.MAGMA_BLOCK, 4)
   );
   private static final BossArenaBuilder.Theme DEFAULT_THEME = new BossArenaBuilder.Theme(
      Material.STONE_BRICKS, Material.COBBLESTONE_WALL, Material.GLOWSTONE, 4
   );

   public BossArenaBuilder.ArenaResult build(Location origin, String bossId) {
      BossArenaBuilder.Theme theme = THEMES.getOrDefault(bossId, DEFAULT_THEME);
      World world = origin.getWorld();
      int centerX = origin.getBlockX();
      int centerZ = origin.getBlockZ();
      int baseY = this.averageGroundY(world, centerX, centerZ);
      Map<String, BossArenaBuilder.BlockChange> recorded = new LinkedHashMap<>();
      this.clearAndLevel(world, centerX, centerZ, baseY, theme, recorded);
      this.placePerimeter(world, centerX, centerZ, baseY, theme, recorded);
      this.scatterAccents(world, centerX, centerZ, baseY, theme, recorded);
      Location spawnAt = new Location(world, centerX + 0.5, baseY + 1, centerZ + 0.5);
      return new BossArenaBuilder.ArenaResult(spawnAt, new BossArenaBuilder.ArenaSnapshot(world, new ArrayList<>(recorded.values())));
   }

   /**
    * True when the footprint of an arena at this spot contains blocks that look player-made (containers, doors,
    * planks, glass, rails and the like). Spawning skips those spots so a boss never levels someone's base.
    */
   public boolean looksPlayerMade(Location origin) {
      World world = origin.getWorld();
      int centerX = origin.getBlockX();
      int centerZ = origin.getBlockZ();
      int baseY = this.averageGroundY(world, centerX, centerZ);
      int artificial = 0;
      for (int dx = -RADIUS; dx <= RADIUS; dx++) {
         for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
            for (int y = baseY - 1; y <= baseY + CLEARANCE_HEIGHT; y++) {
               Block block = world.getBlockAt(centerX + dx, y, centerZ + dz);
               Material type = block.getType();
               if (type.isAir()) continue;
               if (block.getState(false) instanceof TileState || isArtificial(type)) {
                  if (++artificial >= 3) return true;
               }
            }
         }
      }
      return false;
   }

   private static boolean isArtificial(Material type) {
      String n = type.name();
      return n.contains("PLANKS") || n.contains("BRICK") || n.contains("GLASS") || n.contains("WOOL") || n.contains("CONCRETE")
         || n.contains("DOOR") || n.contains("FENCE") || n.contains("STAIRS") || n.contains("SLAB") || n.contains("TORCH")
         || n.contains("LANTERN") || n.contains("BED") || n.contains("CARPET") || n.contains("BANNER") || n.contains("RAIL")
         || n.contains("REDSTONE") || n.contains("HOPPER") || n.contains("PISTON") || n.contains("OBSERVER") || n.contains("LEVER")
         || n.contains("BUTTON") || n.contains("PRESSURE_PLATE") || n.contains("LADDER") || n.contains("SCAFFOLDING")
         || n.contains("TABLE") || n.contains("ANVIL") || n.contains("FURNACE") || n.contains("CHEST") || n.contains("BARREL")
         || n.contains("SIGN") || n.contains("POLISHED") || n.contains("CAMPFIRE") || n.contains("BOOKSHELF")
         || n.contains("GLAZED") || n.contains("STRIPPED") || n.contains("TNT") || n.contains("SPAWNER");
   }

   public static void restore(BossArenaBuilder.ArenaSnapshot snapshot) {
      if (snapshot != null) {
         for (BossArenaBuilder.BlockChange change : snapshot.changes) {
            Block block = snapshot.world.getBlockAt(change.x(), change.y(), change.z());
            // a block someone changed while the arena stood is theirs now; leave it
            // (water or lava that has flowed into a cleared space doesn't count as a player change)
            Material now = block.getType();
            if (change.placed() != null && now != change.placed() && now != Material.WATER && now != Material.LAVA) continue;
            block.setBlockData(change.original(), false);
         }
         snapshot.discard();
      }
   }

   /**
    * Arenas that were still standing when the server stopped or crashed are on disk; put those blocks back.
    * A file whose world isn't loaded is kept for next time.
    */
   public static int restoreLeftovers(StrataModule plugin) {
      File[] files = new File(plugin.getDataFolder(), "arenas").listFiles((dir, name) -> name.endsWith(".txt"));
      if (files == null) {
         return 0;
      }
      int restored = 0;
      for (File file : files) {
         try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            World world = lines.isEmpty() ? null : Bukkit.getWorld(lines.get(0));
            if (world == null) {
               continue;
            }
            for (String line : lines.subList(1, lines.size())) {
               String[] parts = line.split(",", 5);
               // current lines are x,y,z,PLACED_MATERIAL,originaldata; older files are x,y,z,originaldata
               boolean current = parts.length == 5 && !parts[3].contains(":");
               String data = current ? parts[4] : String.join(",", java.util.Arrays.copyOfRange(parts, 3, parts.length));
               if (parts.length >= 4) {
                  Block block = world.getBlockAt(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                  Material now = block.getType();
                  if (current && now != Material.valueOf(parts[3]) && now != Material.WATER && now != Material.LAVA) continue;
                  block.setBlockData(Bukkit.createBlockData(data), false);
               }
            }
            Files.deleteIfExists(file.toPath());
            restored++;
         } catch (IOException | IllegalArgumentException e) {
            plugin.getLogger().warning("Couldn't restore leftover arena " + file.getName() + ": " + e.getMessage());
         }
      }
      return restored;
   }

   private int averageGroundY(World world, int centerX, int centerZ) {
      long total = 0L;
      int samples = 0;

      for (int dx = -9; dx <= 9; dx += 3) {
         for (int dz = -9; dz <= 9; dz += 3) {
            if (dx * dx + dz * dz <= 81) {
               total += world.getHighestBlockYAt(centerX + dx, centerZ + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
               samples++;
            }
         }
      }

      int avg = samples > 0 ? (int)(total / samples) : world.getHighestBlockYAt(centerX, centerZ, HeightMap.MOTION_BLOCKING_NO_LEAVES);
      return Math.min(avg, world.getMaxHeight() - 6 - 2);
   }

   private void clearAndLevel(
      World world, int centerX, int centerZ, int baseY, BossArenaBuilder.Theme theme, Map<String, BossArenaBuilder.BlockChange> recorded
   ) {
      int minY = world.getMinHeight();

      for (int dx = -9; dx <= 9; dx++) {
         for (int dz = -9; dz <= 9; dz++) {
            if (dx * dx + dz * dz <= 81) {
               int x = centerX + dx;
               int z = centerZ + dz;
               this.setTracked(world, x, baseY, z, theme.floor(), recorded);

               for (int y = baseY + 1; y <= baseY + 6; y++) {
                  this.setTracked(world, x, y, z, Material.AIR, recorded);
               }

               for (int y = baseY - 1; y >= Math.max(minY, baseY - 3); y--) {
                  if (!world.getBlockAt(x, y, z).getType().isSolid()) {
                     this.setTracked(world, x, y, z, Material.STONE, recorded);
                  }
               }
            }
         }
      }
   }

   private void placePerimeter(
      World world, int centerX, int centerZ, int baseY, BossArenaBuilder.Theme theme, Map<String, BossArenaBuilder.BlockChange> recorded
   ) {
      int steps = 20;

      for (int i = 0; i < steps; i++) {
         double angle = (Math.PI * 2) * i / steps;
         int x = centerX + (int)Math.round(Math.cos(angle) * 9.0);
         int z = centerZ + (int)Math.round(Math.sin(angle) * 9.0);
         this.setTracked(world, x, baseY + 1, z, theme.perimeter(), recorded);
         if (i % 4 == 0) {
            this.setTracked(world, x, baseY + 2, z, theme.perimeter(), recorded);
         }
      }
   }

   private void scatterAccents(
      World world, int centerX, int centerZ, int baseY, BossArenaBuilder.Theme theme, Map<String, BossArenaBuilder.BlockChange> recorded
   ) {
      ThreadLocalRandom random = ThreadLocalRandom.current();

      for (int i = 0; i < theme.accentCount(); i++) {
         double angle = random.nextDouble(0.0, Math.PI * 2);
         double dist = random.nextDouble(2.0, 7.0);
         int x = centerX + (int)Math.round(Math.cos(angle) * dist);
         int z = centerZ + (int)Math.round(Math.sin(angle) * dist);
         this.setTracked(world, x, baseY + 1, z, theme.accent(), recorded);
      }
   }

   private void setTracked(World world, int x, int y, int z, Material material, Map<String, BossArenaBuilder.BlockChange> recorded) {
      Block block = world.getBlockAt(x, y, z);
      // only the block data is remembered, so anything holding contents (chests, furnaces, signs...) is left alone
      if (block.getState(false) instanceof TileState) {
         return;
      }
      String key = x + "," + y + "," + z;
      BossArenaBuilder.BlockChange earlier = recorded.get(key);
      // the first change keeps the true original; later ones just update what the arena left there
      recorded.put(key, new BossArenaBuilder.BlockChange(x, y, z, earlier != null ? earlier.original() : block.getBlockData(), material));
      block.setType(material, false);
   }

   public static final class ArenaResult {
      public final Location spawnLocation;
      public final BossArenaBuilder.ArenaSnapshot snapshot;

      private ArenaResult(Location spawnLocation, BossArenaBuilder.ArenaSnapshot snapshot) {
         this.spawnLocation = spawnLocation;
         this.snapshot = snapshot;
      }
   }

   public static final class ArenaSnapshot {
      private final World world;
      private final List<BossArenaBuilder.BlockChange> changes;
      private volatile File file;
      private volatile boolean discarded;

      private ArenaSnapshot(World world, List<BossArenaBuilder.BlockChange> changes) {
         this.world = world;
         this.changes = changes;
      }

      /** Writes the original blocks to disk (off the main thread) so a restart or crash can still put them back. */
      public void persist(StrataModule plugin, UUID bossId) {
         File target = new File(new File(plugin.getDataFolder(), "arenas"), bossId + ".txt");
         this.file = target;
         List<String> lines = new ArrayList<>(this.changes.size() + 1);
         lines.add(this.world.getName());
         for (BossArenaBuilder.BlockChange change : this.changes) {
            lines.add(change.x() + "," + change.y() + "," + change.z() + "," + change.placed().name() + "," + change.original().getAsString());
         }
         Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
               Files.createDirectories(target.toPath().getParent());
               Files.write(target.toPath(), lines, StandardCharsets.UTF_8);
               if (this.discarded) {
                  Files.deleteIfExists(target.toPath());
               }
            } catch (IOException e) {
               plugin.getLogger().warning("Couldn't save arena snapshot " + target.getName() + ": " + e.getMessage());
            }
         });
      }

      private void discard() {
         this.discarded = true;
         File target = this.file;
         if (target != null) {
            try {
               Files.deleteIfExists(target.toPath());
            } catch (IOException ignored) {
               // the leftover is restored (harmlessly) on the next start
            }
         }
      }
   }

   private record BlockChange(int x, int y, int z, BlockData original, Material placed) {
   }

   private record Theme(Material floor, Material perimeter, Material accent, int accentCount) {
   }
}
