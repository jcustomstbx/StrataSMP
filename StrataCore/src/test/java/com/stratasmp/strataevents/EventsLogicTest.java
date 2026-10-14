package com.stratasmp.strataevents;

import static org.junit.jupiter.api.Assertions.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EventsLogicTest {
    private static final ZoneId ZONE = ZoneId.of("Europe/London");

    private static ZonedDateTime at(int day, int hour, int minute) {
        // 2026-10-17 is a Saturday
        return ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ZONE);
    }

    /* ---- schedule ---- */

    @Test
    void slotParses() {
        EventSchedule.Slot slot = EventSchedule.Slot.parse(" saturday  18:30 ");
        assertEquals(DayOfWeek.SATURDAY, slot.day());
        assertEquals(LocalTime.of(18, 30), slot.time());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "SATURDAY", "FUNDAY 18:00", "SATURDAY 25:00", "SATURDAY 6pm", "SATURDAY 18:00 extra"})
    void badSlotsAreRejected(String text) {
        assertNull(EventSchedule.Slot.parse(text), text);
    }

    @Test
    void slotIsDueFromItsTimeForTheCatchUpWindow() {
        EventSchedule.Slot slot = EventSchedule.Slot.parse("SATURDAY 18:00");
        assertNull(EventSchedule.dueKey("boss", slot, at(17, 17, 59), 30), "too early");
        assertEquals("boss:2026-10-17T18:00", EventSchedule.dueKey("boss", slot, at(17, 18, 0), 30));
        assertNotNull(EventSchedule.dueKey("boss", slot, at(17, 18, 29), 30));
        assertNull(EventSchedule.dueKey("boss", slot, at(17, 18, 30), 30), "window over");
        assertNull(EventSchedule.dueKey("boss", slot, at(18, 18, 5), 30), "wrong day");
    }

    @Test
    void dueKeyIsStableSoItCanBeRecordedAsFired() {
        EventSchedule.Slot slot = EventSchedule.Slot.parse("SATURDAY 18:00");
        assertEquals(EventSchedule.dueKey("boss", slot, at(17, 18, 1), 30), EventSchedule.dueKey("boss", slot, at(17, 18, 20), 30));
        assertNotEquals(EventSchedule.dueKey("boss", slot, at(17, 18, 1), 30), EventSchedule.dueKey("koth", slot, at(17, 18, 1), 30));
    }

    @Test
    void minutesUntilCountsDownAndRoundsUp() {
        EventSchedule.Slot slot = EventSchedule.Slot.parse("SATURDAY 18:00");
        assertEquals(10, EventSchedule.minutesUntil(slot, at(17, 17, 50)));
        assertEquals(1, EventSchedule.minutesUntil(slot, at(17, 17, 59)));
        assertEquals(-1, EventSchedule.minutesUntil(slot, at(17, 18, 0)));
        assertEquals(-1, EventSchedule.minutesUntil(slot, at(16, 17, 50)));
    }

    @Test
    void nextOccurrenceIsAlwaysInTheFuture() {
        EventSchedule.Slot slot = EventSchedule.Slot.parse("SATURDAY 18:00");
        assertEquals(at(17, 18, 0), EventSchedule.next(slot, at(17, 12, 0)));
        assertEquals(at(24, 18, 0), EventSchedule.next(slot, at(17, 18, 0)));
        assertEquals(at(24, 18, 0), EventSchedule.next(slot, at(18, 9, 0)));
    }

    /* ---- login streaks ---- */

    @Test
    void streakGrowsOnConsecutiveDaysAndPaysOncePerDay() {
        LoginStreaks streaks = new LoginStreaks();
        UUID id = UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 10, 12);
        assertEquals(1, streaks.claim(id, day).streak());
        assertNull(streaks.claim(id, day), "second login the same day pays nothing");
        assertEquals(2, streaks.claim(id, day.plusDays(1)).streak());
        assertEquals(3, streaks.claim(id, day.plusDays(2)).streak());
        assertEquals(3, streaks.best(id));
    }

    @Test
    void missingADayResetsTheStreakButKeepsTheBest() {
        LoginStreaks streaks = new LoginStreaks();
        UUID id = UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 10, 12);
        streaks.claim(id, day);
        streaks.claim(id, day.plusDays(1));
        assertEquals(1, streaks.claim(id, day.plusDays(3)).streak());
        assertEquals(2, streaks.best(id));
    }

    @Test
    void currentStreakIsBrokenAfterAMissedDay() {
        LoginStreaks streaks = new LoginStreaks();
        UUID id = UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 10, 12);
        streaks.claim(id, day);
        assertEquals(1, streaks.currentStreak(id, day));
        assertEquals(1, streaks.currentStreak(id, day.plusDays(1)), "still alive until the day is missed");
        assertEquals(0, streaks.currentStreak(id, day.plusDays(2)));
        assertEquals(0, streaks.currentStreak(UUID.randomUUID(), day));
        assertTrue(streaks.claimedToday(id, day));
        assertFalse(streaks.claimedToday(id, day.plusDays(1)));
    }

    @Test
    void clockGoingBackwardsNeverDoubleClaims() {
        LoginStreaks streaks = new LoginStreaks();
        UUID id = UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 10, 12);
        streaks.claim(id, day);
        assertNull(streaks.claim(id, day.minusDays(1)));
    }

    @Test
    void rewardGrowsThenLevelsOff() {
        assertEquals(25, LoginStreaks.stratasFor(1, 25, 25, 7));
        assertEquals(50, LoginStreaks.stratasFor(2, 25, 25, 7));
        assertEquals(175, LoginStreaks.stratasFor(7, 25, 25, 7));
        assertEquals(175, LoginStreaks.stratasFor(60, 25, 25, 7));
        assertEquals(25, LoginStreaks.stratasFor(0, 25, 25, 7));
        assertEquals(0, LoginStreaks.stratasFor(5, -10, -5, 7));
    }

    @Test
    void streaksSurviveASaveAndLoad() {
        LoginStreaks streaks = new LoginStreaks();
        UUID id = UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 10, 12);
        streaks.claim(id, day);
        streaks.claim(id, day.plusDays(1));
        LoginStreaks loaded = new LoginStreaks();
        loaded.load(streaks.snapshot());
        assertEquals(2, loaded.currentStreak(id, day.plusDays(1)));
        assertEquals(3, loaded.claim(id, day.plusDays(2)).streak());
    }

    /* ---- hill ---- */

    @Test
    void holdingTheHillAloneWinsAtTheTarget() {
        HillState hill = new HillState(5, true);
        for (int i = 1; i < 5; i++) assertEquals(HillState.Status.CAPTURING, hill.tick(Set.of("a")));
        assertEquals(HillState.Status.WON, hill.tick(Set.of("a")));
        assertTrue(hill.won());
        assertEquals("a", hill.capturer());
        assertEquals(HillState.Status.WON, hill.tick(Set.of("b")), "finished stays finished");
    }

    @Test
    void contestingFreezesProgress() {
        HillState hill = new HillState(10, true);
        hill.tick(Set.of("a"));
        hill.tick(Set.of("a"));
        assertEquals(HillState.Status.CONTESTED, hill.tick(Set.of("a", "b")));
        assertEquals(2, hill.progress());
        assertEquals("a", hill.capturer());
        hill.tick(Set.of("a"));
        assertEquals(3, hill.progress());
    }

    @Test
    void aNewHolderStartsFromZero() {
        HillState hill = new HillState(10, true);
        for (int i = 0; i < 6; i++) hill.tick(Set.of("a"));
        hill.tick(Set.of("b"));
        assertEquals("b", hill.capturer());
        assertEquals(1, hill.progress());
    }

    @Test
    void emptyHillDrainsThenForgetsTheCapturer() {
        HillState hill = new HillState(10, true);
        hill.tick(Set.of("a"));
        hill.tick(Set.of("a"));
        assertEquals(HillState.Status.EMPTY, hill.tick(Set.of()));
        assertEquals(1, hill.progress());
        hill.tick(Set.of());
        assertEquals(0, hill.progress());
        assertNull(hill.capturer());
        hill.tick(Set.of());
        assertEquals(0, hill.progress(), "never negative");
    }

    @Test
    void progressIsKeptWhenDrainIsOff() {
        HillState hill = new HillState(10, false);
        hill.tick(Set.of("a"));
        hill.tick(Set.of());
        hill.tick(Set.of());
        assertEquals(1, hill.progress());
        assertEquals("a", hill.capturer());
    }

    @Test
    void targetIsAtLeastOneSecond() {
        assertEquals(HillState.Status.WON, new HillState(0, true).tick(Set.of("a")));
    }

    /* ---- zone ---- */

    @Test
    void zoneIsACylinder() {
        KothZone zone = new KothZone("hill", "world", 100.5, 64, 200.5, 5, 10);
        assertTrue(zone.contains("world", 100.5, 64, 200.5));
        assertTrue(zone.contains("world", 105.5, 74, 200.5), "on the edge");
        assertFalse(zone.contains("world", 105.6, 64, 200.5), "outside the radius");
        assertFalse(zone.contains("world", 100.5, 74.1, 200.5), "above the zone");
        assertFalse(zone.contains("nether", 100.5, 64, 200.5), "wrong world");
        assertFalse(zone.contains("world", 105, 64, 205), "corner is outside the circle");
    }

    @Test
    void shippedConfigSlotsAndRewardsAreValid() throws Exception {
        var cfg = new org.bukkit.configuration.file.YamlConfiguration();
        try (var in = getClass().getResourceAsStream("/modules/StrataEvents/config.yml")) {
            cfg.loadFromString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        assertFalse(WeeklyBossFeatureAccess.slots(cfg.getStringList("weekly-boss.slots")).isEmpty());
        assertFalse(WeeklyBossFeatureAccess.slots(cfg.getStringList("koth.slots")).isEmpty());
        for (String spec : cfg.getStringList("koth.rewards.items")) {
            assertNotNull(com.stratasmp.stratakeystones.KeystoneRewards.parse(spec), spec);
        }
        assertEquals(cfg.getStringList("weekly-boss.slots").size(), WeeklyBossFeatureAccess.slots(cfg.getStringList("weekly-boss.slots")).size());
    }

    /** WeeklyBossFeature is package-private; this keeps the test in the same package without widening it. */
    private static final class WeeklyBossFeatureAccess {
        static java.util.List<EventSchedule.Slot> slots(java.util.List<String> raw) {
            return WeeklyBossFeature.slots(raw);
        }
    }
}
