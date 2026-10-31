package com.stratasmp.strataclear;

import java.util.List;

/** A once-a-second countdown that says when to warn and when to clear. Pure, so it can be tested directly. */
public final class ClearCountdown {

    public enum Kind { NOTHING, WARN, CLEAR }

    /** What happened on one tick. */
    public record Event(Kind kind, int secondsLeft) {
    }

    private final int intervalSeconds;
    private final List<Integer> warnings;
    private int secondsLeft;

    public ClearCountdown(int intervalSeconds, List<Integer> warnings) {
        this.intervalSeconds = Math.max(5, intervalSeconds);
        this.warnings = List.copyOf(warnings);
        this.secondsLeft = this.intervalSeconds;
    }

    /** Advances one second. */
    public Event tick() {
        secondsLeft--;
        if (secondsLeft <= 0) {
            secondsLeft = intervalSeconds;
            return new Event(Kind.CLEAR, 0);
        }
        if (warnings.contains(secondsLeft) && secondsLeft < intervalSeconds) {
            return new Event(Kind.WARN, secondsLeft);
        }
        return new Event(Kind.NOTHING, secondsLeft);
    }

    public int secondsLeft() {
        return secondsLeft;
    }

    /** Starts a fresh interval, e.g. after a manual clear. */
    public void reset() {
        secondsLeft = intervalSeconds;
    }
}
