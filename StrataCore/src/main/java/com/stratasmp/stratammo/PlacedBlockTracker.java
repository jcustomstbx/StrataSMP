package com.stratasmp.stratammo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class PlacedBlockTracker implements Listener {
   // every block any player places is remembered, so the oldest are dropped once this many pile up
   private static final int MAX_TRACKED = 250_000;

   private final Set<String> placed = Collections.newSetFromMap(new LinkedHashMap<String, Boolean>() {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
         return size() > MAX_TRACKED;
      }
   });

   @EventHandler(
      ignoreCancelled = true
   )
   public void onPlace(BlockPlaceEvent event) {
      this.placed.add(this.key(event.getBlock().getLocation()));
   }

   public boolean wasPlaced(Location location) {
      return this.placed.contains(this.key(location));
   }

   public void forget(Location location) {
      this.placed.remove(this.key(location));
   }

   private String key(Location loc) {
      return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
   }
}
