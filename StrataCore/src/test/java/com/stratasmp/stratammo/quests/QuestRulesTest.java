package com.stratasmp.stratammo.quests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import com.stratasmp.stratammo.Skill;
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

    private static QuestDef weekly(String id, Skill skill) {
        return new QuestDef(id, id, ObjectiveType.MINE_BLOCK, Set.of(), 10, skill, 100, 50L);
    }

    @Test
    void weeklyPicksPreferDifferentSkills() {
        List<QuestDef> pool = List.of(weekly("a", Skill.MINING), weekly("b", Skill.MINING), weekly("c", Skill.MINING),
                weekly("d", Skill.FARMING), weekly("e", Skill.FISHING));
        for (long seed = 0; seed < 50; seed++) {
            List<QuestDef> picked = QuestRules.pickWeekly(pool, 3, new Random(seed));
            assertEquals(3, picked.size());
            Set<Skill> skills = new HashSet<>();
            picked.forEach(q -> skills.add(q.skill()));
            assertEquals(3, skills.size(), "seed " + seed);
        }
    }

    @Test
    void weeklyRepeatsASkillOnlyWhenThePoolRunsOutOfSkills() {
        List<QuestDef> pool = List.of(weekly("a", Skill.MINING), weekly("b", Skill.MINING), weekly("c", Skill.FARMING));
        List<QuestDef> picked = QuestRules.pickWeekly(pool, 3, new Random(1));
        assertEquals(3, picked.size());
        assertEquals(3, new HashSet<>(picked).size());
    }

    @Test
    void weeklyNeverReturnsMoreThanThePoolOrDuplicates() {
        List<QuestDef> pool = List.of(weekly("a", Skill.MINING), weekly("b", Skill.FARMING));
        assertEquals(2, QuestRules.pickWeekly(pool, 5, new Random(7)).size());
        assertEquals(0, QuestRules.pickWeekly(List.of(), 3, new Random(7)).size());
        assertEquals(0, QuestRules.pickWeekly(pool, 0, new Random(7)).size());
    }

    @Test
    void menuBarFillsInProportion() {
        assertEquals("\u2591".repeat(10), QuestGui.bar(0, 10));
        assertEquals("\u2588".repeat(5) + "\u2591".repeat(5), QuestGui.bar(5, 10));
        assertEquals("\u2588".repeat(10), QuestGui.bar(10, 10));
        assertEquals("\u2591".repeat(10), QuestGui.bar(3, 0));
    }
}
