package com.stratasmp.stratammo.quests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class QuestRulesTest {

    @Test
    void weekStartsOnTheResetDay() {
        // 2026-10-30 is a Friday; the week that started on Monday is 2026-10-26
        assertEquals("2026-10-26", QuestRules.weekKey(LocalDate.of(2026, 10, 30), DayOfWeek.MONDAY));
        assertEquals("2026-10-26", QuestRules.weekKey(LocalDate.of(2026, 10, 26), DayOfWeek.MONDAY));
        assertEquals("2026-10-26", QuestRules.weekKey(LocalDate.of(2026, 11, 1), DayOfWeek.MONDAY));
    }

    @Test
    void weekChangesExactlyOnTheResetDay() {
        String sunday = QuestRules.weekKey(LocalDate.of(2026, 11, 1), DayOfWeek.MONDAY);
        String monday = QuestRules.weekKey(LocalDate.of(2026, 11, 2), DayOfWeek.MONDAY);
        assertFalse(sunday.equals(monday));
    }

    @Test
    void chainAdvancesOneStepAtATimeAndResetsProgress() {
        QuestRules.ChainState chain = new QuestRules.ChainState();
        assertEquals(QuestRules.Result.NONE, chain.add(40, 100, 3));
        assertEquals(40, chain.progress());
        assertEquals(QuestRules.Result.STEP_DONE, chain.add(60, 100, 3));
        assertEquals(1, chain.step());
        assertEquals(0, chain.progress());
        assertFalse(chain.done());
    }

    @Test
    void oversizedProgressDoesNotSkipSteps() {
        QuestRules.ChainState chain = new QuestRules.ChainState();
        assertEquals(QuestRules.Result.STEP_DONE, chain.add(1000, 10, 3));
        assertEquals(1, chain.step());
        assertEquals(0, chain.progress());
    }

    @Test
    void finishingTheLastStepCompletesTheChainOnce() {
        QuestRules.ChainState chain = new QuestRules.ChainState(1, 0, false);
        assertEquals(QuestRules.Result.CHAIN_DONE, chain.add(5, 5, 2));
        assertTrue(chain.done());
        // a finished chain pays nothing further, however much progress arrives
        assertEquals(QuestRules.Result.NONE, chain.add(5, 5, 2));
        assertEquals(QuestRules.Result.NONE, chain.add(500, 5, 2));
        assertTrue(chain.done());
    }

    @Test
    void invalidAmountsChangeNothing() {
        QuestRules.ChainState chain = new QuestRules.ChainState();
        assertEquals(QuestRules.Result.NONE, chain.add(0, 10, 3));
        assertEquals(QuestRules.Result.NONE, chain.add(-5, 10, 3));
        assertEquals(QuestRules.Result.NONE, chain.add(5, 0, 3));
        assertEquals(0, chain.progress());
        assertEquals(0, chain.step());
    }

    @Test
    void savedStateRestoresExactly() {
        QuestRules.ChainState chain = new QuestRules.ChainState(2, 7, false);
        assertEquals(2, chain.step());
        assertEquals(7, chain.progress());
        assertEquals(QuestRules.Result.STEP_DONE, chain.add(3, 10, 5));
        assertEquals(3, chain.step());
    }

    @Test
    void menuBarFillsInProportion() {
        assertEquals("\u2591".repeat(10), QuestGui.bar(0, 10));
        assertEquals("\u2588".repeat(5) + "\u2591".repeat(5), QuestGui.bar(5, 10));
        assertEquals("\u2588".repeat(10), QuestGui.bar(10, 10));
        assertEquals("\u2591".repeat(10), QuestGui.bar(3, 0));
    }
}
