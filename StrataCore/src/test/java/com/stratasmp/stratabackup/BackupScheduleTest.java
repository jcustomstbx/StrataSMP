package com.stratasmp.stratabackup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class BackupScheduleTest {

    private static final LocalTime AT = LocalTime.of(4, 30);

    private static LocalDateTime at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 11, day, hour, minute);
    }

    @Test
    void neverRunBeforeTheConfiguredTime() {
        assertFalse(BackupSchedule.isDue(at(10, 4, 29), null, 3, AT));
        assertTrue(BackupSchedule.isDue(at(10, 4, 30), null, 3, AT));
    }

    @Test
    void firstEverRunIsDueOnceTheTimeHasPassed() {
        assertTrue(BackupSchedule.isDue(at(10, 15, 0), null, 3, AT));
    }

    @Test
    void waitsTheFullIntervalBetweenRuns() {
        LocalDate last = LocalDate.of(2026, 11, 10);
        assertFalse(BackupSchedule.isDue(at(10, 23, 0), last, 3, AT));
        assertFalse(BackupSchedule.isDue(at(12, 12, 0), last, 3, AT));
        assertTrue(BackupSchedule.isDue(at(13, 4, 30), last, 3, AT));
        assertTrue(BackupSchedule.isDue(at(20, 9, 0), last, 3, AT));
    }

    @Test
    void anIntervalBelowOneDayStillMeansDaily() {
        LocalDate last = LocalDate.of(2026, 11, 10);
        assertFalse(BackupSchedule.isDue(at(10, 6, 0), last, 0, AT));
        assertTrue(BackupSchedule.isDue(at(11, 6, 0), last, 0, AT));
    }

    @Test
    void requestFieldsAreKeptToASafeAlphabet() {
        assertEquals("_Denton_", BackupSchedule.safe("_Denton_"));
        assertEquals("a_b_c", BackupSchedule.safe("a b;c"));
        assertEquals("unknown", BackupSchedule.safe(""));
        assertEquals("unknown", BackupSchedule.safe(null));
        assertEquals(32, BackupSchedule.safe("x".repeat(80)).length());
    }
}
