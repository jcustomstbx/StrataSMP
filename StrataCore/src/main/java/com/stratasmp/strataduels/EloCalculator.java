package com.stratasmp.strataduels;

public final class EloCalculator {
   private EloCalculator() {
   }

   public static double expectedScore(double ratingSelf, double ratingOpponent) {
      return 1.0 / (1.0 + Math.pow(10.0, (ratingOpponent - ratingSelf) / 400.0));
   }

   public static int kFactorFor(int gamesPlayed, int placementGames, int placementK, int standardK) {
      return gamesPlayed < placementGames ? placementK : standardK;
   }

   public static double newRating(double rating, double expected, double actualScore, int kFactor, double floor) {
      double updated = rating + kFactor * (actualScore - expected);
      return Math.max(floor, updated);
   }

   public static EloCalculator.MatchResult applyMatch(
      double winnerElo, int winnerGamesPlayed, double loserElo, int loserGamesPlayed, int placementGames, int placementK, int standardK, double floor
   ) {
      double winnerExpected = expectedScore(winnerElo, loserElo);
      double loserExpected = expectedScore(loserElo, winnerElo);
      int winnerK = kFactorFor(winnerGamesPlayed, placementGames, placementK, standardK);
      int loserK = kFactorFor(loserGamesPlayed, placementGames, placementK, standardK);
      double newWinnerElo = newRating(winnerElo, winnerExpected, 1.0, winnerK, floor);
      double newLoserElo = newRating(loserElo, loserExpected, 0.0, loserK, floor);
      return new EloCalculator.MatchResult(newWinnerElo, newLoserElo);
   }

   public record MatchResult(double winnerNewElo, double loserNewElo) {
   }
}
