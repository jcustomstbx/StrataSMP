package com.stratasmp.strataevents;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Weekly time slots such as "SATURDAY 18:00", and the checks the event runner makes against the clock. */
public final class EventSchedule {
    private EventSchedule() {}

    public record Slot(DayOfWeek day, LocalTime time) {
        /** @return the slot, or null if the text isn't "DAY HH:mm". */
        public static Slot parse(String text) {
            if (text == null) return null;
            String[] parts = text.trim().split("\\s+");
            if (parts.length != 2) return null;
            try {
                return new Slot(DayOfWeek.valueOf(parts[0].toUpperCase(Locale.ROOT)), LocalTime.parse(parts[1]));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                return null;
            }
        }

        /** Where this slot falls on the date of {@code now}, in now's zone. */
        ZonedDateTime on(ZonedDateTime now) {
            return now.toLocalDate().atTime(time).atZone(now.getZone());
        }
    }

    /**
     * The key identifying today's occurrence of the slot when it is due now, otherwise null. "Due" starts at the slot
     * time and lasts {@code catchUpMinutes}, so a server that was down at the exact minute still runs it shortly after.
     */
    public static String dueKey(String event, Slot slot, ZonedDateTime now, int catchUpMinutes) {
        if (now.getDayOfWeek() != slot.day()) return null;
        ZonedDateTime start = slot.on(now);
        if (now.isBefore(start) || !now.isBefore(start.plusMinutes(Math.max(1, catchUpMinutes)))) return null;
        return event + ":" + start.toLocalDate() + "T" + slot.time();
    }

    /** Whole minutes until today's occurrence (rounded up), or -1 when the slot isn't later today. */
    public static long minutesUntil(Slot slot, ZonedDateTime now) {
        if (now.getDayOfWeek() != slot.day()) return -1;
        ZonedDateTime start = slot.on(now);
        if (!now.isBefore(start)) return -1;
        long seconds = Duration.between(now, start).getSeconds();
        return (seconds + 59) / 60;
    }

    /** The next time the slot occurs strictly after {@code now}. */
    public static ZonedDateTime next(Slot slot, ZonedDateTime now) {
        ZonedDateTime candidate = slot.on(now);
        while (candidate.getDayOfWeek() != slot.day() || !candidate.isAfter(now)) {
            candidate = candidate.plusDays(1).toLocalDate().atTime(slot.time()).atZone(now.getZone());
        }
        return candidate;
    }
}
