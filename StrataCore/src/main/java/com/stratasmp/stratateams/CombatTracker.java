package com.stratasmp.stratateams;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class CombatTracker {
   private static final String DUEL_TAG_KEY = "strataduels-active";
   private final long combatDurationMillis;
   private final Map<UUID, Long> lastCombatAt = new HashMap<>();

   public CombatTracker(long combatDurationSeconds) {
      this.combatDurationMillis = combatDurationSeconds * 1000L;
   }

   public void markInCombat(UUID uuid) {
      this.lastCombatAt.put(uuid, System.currentTimeMillis());
   }

   public void clearCombat(UUID uuid) {
      this.lastCombatAt.remove(uuid);
   }

   public boolean isInCombat(UUID uuid) {
      if (this.isDuelTagged(uuid)) {
         return false;
      } else {
         Long last = this.lastCombatAt.get(uuid);
         return last != null && System.currentTimeMillis() - last < this.combatDurationMillis;
      }
   }

   private boolean isDuelTagged(UUID uuid) {
      Player player = Bukkit.getPlayer(uuid);
      return player != null && player.hasMetadata("strataduels-active");
   }

   public long secondsRemaining(UUID uuid) {
      Long last = this.lastCombatAt.get(uuid);
      if (last == null) {
         return 0L;
      } else {
         long remainingMillis = this.combatDurationMillis - (System.currentTimeMillis() - last);
         return Math.max(0L, (remainingMillis + 999L) / 1000L);
      }
   }
}
