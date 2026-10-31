package com.stratasmp.strataclear;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClearCountdownTest {

    private static List<ClearCountdown.Event> run(ClearCountdown countdown, int ticks) {
        List<ClearCountdown.Event> events = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            ClearCountdown.Event event = countdown.tick();
            if (event.kind() != ClearCountdown.Kind.NOTHING) {
                events.add(event);
            }
        }
        return events;
    }

    @Test
    void warnsAtEachConfiguredSecondThenClears() {
        ClearCountdown countdown = new ClearCountdown(600, List.of(60, 30, 10, 5, 4, 3, 2, 1));
        List<ClearCountdown.Event> events = run(countdown, 600);
        List<Integer> warned = events.stream().filter(e -> e.kind() == ClearCountdown.Kind.WARN)
                .map(ClearCountdown.Event::secondsLeft).toList();
        assertEquals(List.of(60, 30, 10, 5, 4, 3, 2, 1), warned);
        assertEquals(ClearCountdown.Kind.CLEAR, events.get(events.size() - 1).kind());
    }

    @Test
    void clearsExactlyOncePerInterval() {
        ClearCountdown countdown = new ClearCountdown(600, List.of(60));
        long clears = run(countdown, 1800).stream().filter(e -> e.kind() == ClearCountdown.Kind.CLEAR).count();
        assertEquals(3, clears);
    }

    @Test
    void sixtySecondWarningArrivesSixtySecondsBeforeTheClear() {
        ClearCountdown countdown = new ClearCountdown(600, List.of(60));
        for (int i = 0; i < 539; i++) {
            assertEquals(ClearCountdown.Kind.NOTHING, countdown.tick().kind());
        }
        ClearCountdown.Event warn = countdown.tick();
        assertEquals(ClearCountdown.Kind.WARN, warn.kind());
        assertEquals(60, warn.secondsLeft());
    }

    @Test
    void warningsLongerThanTheIntervalAreIgnored() {
        ClearCountdown countdown = new ClearCountdown(30, List.of(60, 10));
        List<Integer> warned = run(countdown, 30).stream().filter(e -> e.kind() == ClearCountdown.Kind.WARN)
                .map(ClearCountdown.Event::secondsLeft).toList();
        assertEquals(List.of(10), warned);
    }

    @Test
    void resetStartsAFreshInterval() {
        ClearCountdown countdown = new ClearCountdown(100, List.of());
        run(countdown, 40);
        assertEquals(60, countdown.secondsLeft());
        countdown.reset();
        assertEquals(100, countdown.secondsLeft());
    }
}
