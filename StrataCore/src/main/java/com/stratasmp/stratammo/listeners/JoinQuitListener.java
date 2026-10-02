package com.stratasmp.stratammo.listeners;

import com.stratasmp.stratammo.DataManager;
import java.util.Locale;
import java.util.Set;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinQuitListener implements Listener {
   private final DataManager dataManager;
   private final Set<String> worlds;

   public JoinQuitListener(DataManager dataManager, Set<String> worlds) {
      this.dataManager = dataManager;
      this.worlds = worlds.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      if (inProfile(event.getPlayer().getWorld().getName())) this.dataManager.load(event.getPlayer());
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.dataManager.unload(event.getPlayer());
   }

   @EventHandler
   public void onWorldChange(PlayerChangedWorldEvent event) {
      if (inProfile(event.getPlayer().getWorld().getName())) this.dataManager.load(event.getPlayer());
      else this.dataManager.unload(event.getPlayer());
   }

   private boolean inProfile(String world) {
      return worlds.contains(world.toLowerCase(Locale.ROOT));
   }
}
