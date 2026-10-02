package com.stratasmp.strataduels.arena;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class Arena {
   public final String name;
   public String worldName;
   public double pos1X;
   public double pos1Y;
   public double pos1Z;
   public float pos1Yaw;
   public float pos1Pitch;
   public boolean hasPos1;
   public double pos2X;
   public double pos2Y;
   public double pos2Z;
   public float pos2Yaw;
   public float pos2Pitch;
   public boolean hasPos2;
   public boolean enabled = true;
   public double boundX1;
   public double boundY1;
   public double boundZ1;
   public boolean hasBound1;
   public double boundX2;
   public double boundY2;
   public double boundZ2;
   public boolean hasBound2;
   public boolean inUse = false;
   // free-for-all maps: fighters spread over pos1, pos2 and any extra spawns; never used for 1v1 queues
   public boolean ffa = false;
   public final List<double[]> spawns = new ArrayList<>();
   private volatile Arena.Bounds cage;
   private static final int MIN_HEIGHT_SPAN = 4;
   private static final int AUTO_PAD_BELOW = 1;
   // bumped from 9 - bounds set at a single floor-level Y (the common case: stand
   // at two floor corners and run setbound1/setbound2) only auto-padded 9 blocks
   // above that floor, so anything placed higher than that - cobwebs strung near
   // a tall ceiling, a bridged-up fight - sat outside the reset volume and never
   // got cleared by resetArena(). 24 comfortably covers a normal duel room.
   private static final int AUTO_PAD_ABOVE = 24;
   private static final int MATCH_BOUNDS_MARGIN = 4;

   public Arena(String name) {
      this.name = name;
   }

   public boolean isReady() {
      return this.hasPos1 && this.hasPos2 && this.worldName != null && Bukkit.getWorld(this.worldName) != null;
   }

   public boolean hasBounds() {
      return this.hasBound1 && this.hasBound2;
   }

   public Arena.Bounds computeBounds() {
      int minX = (int)Math.floor(Math.min(this.boundX1, this.boundX2));
      int maxX = (int)Math.floor(Math.max(this.boundX1, this.boundX2));
      int minY = (int)Math.floor(Math.min(this.boundY1, this.boundY2));
      int maxY = (int)Math.floor(Math.max(this.boundY1, this.boundY2));
      int minZ = (int)Math.floor(Math.min(this.boundZ1, this.boundZ2));
      int maxZ = (int)Math.floor(Math.max(this.boundZ1, this.boundZ2));
      if (maxY - minY + 1 < 4) {
         minY--;
         maxY += 9;
      }

      return new Arena.Bounds(minX, maxX, minY, maxY, minZ, maxZ);
   }

   // The box the barrier walls actually enclose, read from the saved baseline. The bounds an admin
   // sets are usually much larger than the room (they have to cover the spawns and anything a match
   // can change), so on their own they can't tell that someone has clipped out through a wall.
   public void setCage(Arena.Bounds cage) {
      this.cage = cage;
   }

   public Arena.Bounds cage() {
      return this.cage;
   }

   /**
    * Where a duelist trying to reach {@code loc} should actually end up, if anywhere - the goal is to
    * make leaving impossible rather than to catch it after the fact (no more forfeiting a match because
    * a pearl tunnelled through a one-block-thick barrier). Uses the barrier cage - the shape of the
    * built room, read off the baseline snapshot - when there is one, since it is far tighter than the
    * admin-set bounds; falls back to the same loose box {@link #contains(Location)} uses otherwise.
    * Returns null when {@code loc} is already fine (nothing to redirect) or is in a different world
    * entirely, which no amount of clamping can fix - callers should cancel the move outright then.
    */
   public Location clampToPlayArea(Location loc) {
      if (!this.hasBounds()) {
         return null;
      }
      World world = Bukkit.getWorld(this.worldName);
      if (world == null || loc.getWorld() == null || !loc.getWorld().equals(world)) {
         return null;
      }
      Arena.Bounds box = this.cage != null ? this.cage : this.loosePlayBounds();
      if (box.contains(loc)) {
         return null;
      }
      double x = clamp(loc.getX(), box.minX() + 0.5, box.maxX() + 0.5);
      double y = clamp(loc.getY(), box.minY() + 0.5, box.maxY() - 0.5);
      double z = clamp(loc.getZ(), box.minZ() + 0.5, box.maxZ() + 0.5);
      return new Location(world, x, y, z, loc.getYaw(), loc.getPitch());
   }

   private static double clamp(double value, double min, double max) {
      return Math.min(Math.max(value, min), max);
   }

   public boolean contains(Location loc) {
      if (!this.hasBounds()) {
         return true;
      }
      World world = Bukkit.getWorld(this.worldName);
      return world != null && loc.getWorld().equals(world) && this.loosePlayBounds().contains(loc);
   }

   /** The admin-set bounds, widened 4 blocks and stretched to cover both spawns - used when there's no barrier cage yet. */
   private Arena.Bounds loosePlayBounds() {
      Arena.Bounds b = this.computeBounds();
      int minX = Math.min(b.minX(), (int)Math.floor(Math.min(this.pos1X, this.pos2X))) - 4;
      int maxX = Math.max(b.maxX(), (int)Math.floor(Math.max(this.pos1X, this.pos2X))) + 4;
      int minY = Math.min(b.minY(), (int)Math.floor(Math.min(this.pos1Y, this.pos2Y))) - 4;
      int maxY = Math.max(b.maxY(), (int)Math.floor(Math.max(this.pos1Y, this.pos2Y))) + 4;
      int minZ = Math.min(b.minZ(), (int)Math.floor(Math.min(this.pos1Z, this.pos2Z))) - 4;
      int maxZ = Math.max(b.maxZ(), (int)Math.floor(Math.max(this.pos1Z, this.pos2Z))) + 4;
      for (double[] s : this.spawns) {
         minX = Math.min(minX, (int)Math.floor(s[0]) - 4);
         maxX = Math.max(maxX, (int)Math.floor(s[0]) + 4);
         minY = Math.min(minY, (int)Math.floor(s[1]) - 4);
         maxY = Math.max(maxY, (int)Math.floor(s[1]) + 4);
         minZ = Math.min(minZ, (int)Math.floor(s[2]) - 4);
         maxZ = Math.max(maxZ, (int)Math.floor(s[2]) + 4);
      }
      return new Arena.Bounds(minX, maxX, minY, maxY, minZ, maxZ);
   }

   /** Every place an FFA fighter can start: pos1, pos2, then any extra spawns added with /duelarena addspawn. */
   public List<Location> ffaSpawns() {
      List<Location> all = new ArrayList<>();
      if (this.hasPos1 && this.location1() != null) {
         all.add(this.location1());
      }
      if (this.hasPos2 && this.location2() != null) {
         all.add(this.location2());
      }
      World world = Bukkit.getWorld(this.worldName);
      if (world != null) {
         for (double[] s : this.spawns) {
            all.add(new Location(world, s[0], s[1], s[2], (float)s[3], (float)s[4]));
         }
      }
      return all;
   }

   public Location location1() {
      World world = Bukkit.getWorld(this.worldName);
      return world == null ? null : new Location(world, this.pos1X, this.pos1Y, this.pos1Z, this.pos1Yaw, this.pos1Pitch);
   }

   public Location location2() {
      World world = Bukkit.getWorld(this.worldName);
      return world == null ? null : new Location(world, this.pos2X, this.pos2Y, this.pos2Z, this.pos2Yaw, this.pos2Pitch);
   }

   public record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
      public long volume() {
         return (long)(this.maxX - this.minX + 1) * (this.maxY - this.minY + 1) * (this.maxZ - this.minZ + 1);
      }

      public boolean contains(Location loc) {
         int x = loc.getBlockX();
         int y = loc.getBlockY();
         int z = loc.getBlockZ();
         return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ;
      }
   }
}
