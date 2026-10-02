package com.stratasmp.strataduels.match;

import com.stratasmp.strataduels.arena.Arena;
import java.util.UUID;
import org.bukkit.scheduler.BukkitTask;

public class DuelMatch {
   public final UUID matchId = UUID.randomUUID();
   public final UUID playerA;
   public final UUID playerB;
   public final int kitA;
   public final int kitB;
   public final Arena arena;
   public DuelMatch.State state = DuelMatch.State.PENDING_TELEPORT;
   public long activeSince;
   public PlayerSnapshot snapshotA;
   public PlayerSnapshot snapshotB;
   public BukkitTask countdownTask;
   public BukkitTask timeoutTask;

   public DuelMatch(UUID playerA, UUID playerB, int kitA, int kitB, Arena arena) {
      this.playerA = playerA;
      this.playerB = playerB;
      this.kitA = kitA;
      this.kitB = kitB;
      this.arena = arena;
   }

   public UUID opponentOf(UUID player) {
      if (this.playerA.equals(player)) {
         return this.playerB;
      } else {
         return this.playerB.equals(player) ? this.playerA : null;
      }
   }

   public boolean involves(UUID player) {
      return this.playerA.equals(player) || this.playerB.equals(player);
   }

   public static enum State {
      PENDING_TELEPORT,
      COUNTDOWN,
      ACTIVE,
      ENDING,
      COMPLETE;
   }
}
