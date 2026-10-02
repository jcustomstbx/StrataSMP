package com.stratasmp.strataduels;

import java.util.UUID;

public class DuelPlayerData {
   public final UUID uuid;
   public String lastKnownName;
   public double seasonElo;
   public int seasonWins;
   public int seasonLosses;
   public int seasonWinStreak;
   public int seasonBestStreak;
   public int seasonGamesPlayed;
   public int lifetimeWins;
   public int lifetimeLosses;
   public int lifetimeGamesPlayed;

   public DuelPlayerData(UUID uuid, String lastKnownName, double startingElo) {
      this.uuid = uuid;
      this.lastKnownName = lastKnownName;
      this.seasonElo = startingElo;
   }

   public void resetSeason(double startingElo) {
      this.seasonElo = startingElo;
      this.seasonWins = 0;
      this.seasonLosses = 0;
      this.seasonWinStreak = 0;
      this.seasonBestStreak = 0;
      this.seasonGamesPlayed = 0;
   }
}
