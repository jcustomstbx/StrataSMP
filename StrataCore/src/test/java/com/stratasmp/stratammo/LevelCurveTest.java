package com.stratasmp.stratammo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LevelCurveTest {

    // level n needs 100 + n * 40 xp, so: 100, 140, 180, ...
    private final LevelCurve uncapped = new LevelCurve(100, 40, 0);

    @Test
    void costGrowsPerLevel() {
        assertEquals(100, uncapped.xpForLevel(0));
        assertEquals(140, uncapped.xpForLevel(1));
        assertEquals(180, uncapped.xpForLevel(2));
    }

    @Test
    void levelAndRemainderFromTotalXp() {
        assertArrayEquals(new int[] {0, 0}, uncapped.levelFromTotalXp(0));
        assertArrayEquals(new int[] {0, 99}, uncapped.levelFromTotalXp(99));
        assertArrayEquals(new int[] {1, 0}, uncapped.levelFromTotalXp(100));
        assertArrayEquals(new int[] {1, 139}, uncapped.levelFromTotalXp(239));
        assertArrayEquals(new int[] {2, 0}, uncapped.levelFromTotalXp(240));
    }

    @Test
    void cappedCurveStopsAtTheCap() {
        LevelCurve capped = new LevelCurve(100, 40, 3);
        assertTrue(capped.isCapped());
        assertFalse(uncapped.isCapped());
        // 100 + 140 + 180 = 420 reaches level 3
        assertArrayEquals(new int[] {3, 0}, capped.levelFromTotalXp(420));
        assertArrayEquals(new int[] {3, 0}, capped.levelFromTotalXp(1_000_000));
    }
}
