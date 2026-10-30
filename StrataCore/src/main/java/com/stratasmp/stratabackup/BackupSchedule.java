package com.stratasmp.stratabackup;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/** When a scheduled backup is due. Pure, so it can be tested without a server. */
public final class BackupSchedule {

    private BackupSchedule() {
    }

    /**
     * @param now       the current local time in the schedule's time zone
     * @param lastRun   date of the last attempt, or null if there has never been one
     * @param everyDays minimum number of days between attempts (at least 1)
     * @param at        earliest time of day an attempt may start
     */
    public static boolean isDue(LocalDateTime now, LocalDate lastRun, int everyDays, LocalTime at) {
        if (now.toLocalTime().isBefore(at)) {
            return false;
        }
        if (lastRun == null) {
            return true;
        }
        return ChronoUnit.DAYS.between(lastRun, now.toLocalDate()) >= Math.max(1, everyDays);
    }

    /** Keeps request fields to a safe alphabet; the host script reads them as plain text. */
    public static String safe(String value) {
        if (value == null || value.isEmpty()) {
            return "unknown";
        }
        String cleaned = value.replaceAll("[^A-Za-z0-9_.-]", "_");
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }
}
