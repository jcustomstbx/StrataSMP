package com.stratasmp.strataduels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EloCalculatorTest {

    @Test
    void equalRatingsExpectAHalf() {
        assertEquals(0.5, EloCalculator.expectedScore(1000, 1000), 1e-9);
    }

    @Test
    void expectedScoresOfTwoPlayersAddUpToOne() {
        double a = EloCalculator.expectedScore(1400, 1000);
        double b = EloCalculator.expectedScore(1000, 1400);
        assertEquals(1.0, a + b, 1e-9);
        assertTrue(a > 0.9);
    }

    @Test
    void equalPlayersSwingByHalfTheKFactor() {
        EloCalculator.MatchResult result = EloCalculator.applyMatch(1000, 50, 1000, 50, 10, 40, 20, 0);
        assertEquals(1010.0, result.winnerNewElo(), 1e-9);
        assertEquals(990.0, result.loserNewElo(), 1e-9);
    }

    @Test
    void anUpsetMovesMoreThanTheExpectedResult() {
        double upset = EloCalculator.applyMatch(1000, 50, 1400, 50, 10, 40, 20, 0).winnerNewElo() - 1000;
        double expected = EloCalculator.applyMatch(1400, 50, 1000, 50, 10, 40, 20, 0).winnerNewElo() - 1400;
        assertTrue(upset > expected);
    }

    @Test
    void ratingsNeverDropBelowTheFloor() {
        // an even match with K=100 would drop the loser to 950; the floor holds them at 980
        EloCalculator.MatchResult result = EloCalculator.applyMatch(1000, 50, 1000, 50, 10, 40, 100, 980);
        assertEquals(980.0, result.loserNewElo(), 1e-9);
        assertEquals(1050.0, result.winnerNewElo(), 1e-9);
    }

    @Test
    void placementGamesUseThePlacementK() {
        assertEquals(40, EloCalculator.kFactorFor(3, 10, 40, 20));
        assertEquals(20, EloCalculator.kFactorFor(10, 10, 40, 20));
        assertEquals(20, EloCalculator.kFactorFor(500, 10, 40, 20));
    }

    @Test
    void winnerGainsWhatTheLoserLosesWhenBothUseTheSameK() {
        EloCalculator.MatchResult result = EloCalculator.applyMatch(1200, 50, 1100, 50, 10, 40, 20, 0);
        assertEquals(2300.0, result.winnerNewElo() + result.loserNewElo(), 1e-6);
    }
}
