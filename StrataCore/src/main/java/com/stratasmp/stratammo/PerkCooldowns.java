package com.stratasmp.stratammo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PerkCooldowns {
   private final Map<UUID, Map<String, Long>> expiries = new ConcurrentHashMap<>();

   public boolean tryTrigger(UUID player, String perkKey, long cooldownSeconds) {
      long now = System.currentTimeMillis();
      Map<String, Long> playerCooldowns = this.expiries.computeIfAbsent(player, k -> new HashMap<>());
      Long expiry = playerCooldowns.get(perkKey);
      if (expiry != null && expiry > now) {
         return false;
      } else {
         playerCooldowns.put(perkKey, now + cooldownSeconds * 1000L);
         return true;
      }
   }

   public long secondsLeft(UUID player, String perkKey) {
      Map<String, Long> playerCooldowns = this.expiries.get(player);
      if (playerCooldowns == null) {
         return 0L;
      } else {
         Long expiry = playerCooldowns.get(perkKey);
         if (expiry == null) {
            return 0L;
         } else {
            long remainingMillis = expiry - System.currentTimeMillis();
            return remainingMillis <= 0L ? 0L : remainingMillis / 1000L + 1L;
         }
      }
   }

   public void clear(UUID player) {
      this.expiries.remove(player);
   }
}
