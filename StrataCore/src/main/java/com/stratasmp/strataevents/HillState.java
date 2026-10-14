package com.stratasmp.strataevents;

import java.util.Set;

/**
 * King-of-the-hill capture rules, ticked once per second with the holders currently inside the zone (a team, or a
 * lone player). One holder alone gains a second of capture time; two or more freeze it; an empty hill slowly lets it
 * drain. A different holder taking over starts from zero. Reaching the target wins.
 */
public final class HillState {
    public enum Status { EMPTY, CAPTURING, CONTESTED, WON }

    private final int targetSeconds;
    private final boolean drainWhenEmpty;
    private String capturer;
    private int progress;
    private boolean won;

    public HillState(int targetSeconds, boolean drainWhenEmpty) {
        this.targetSeconds = Math.max(1, targetSeconds);
        this.drainWhenEmpty = drainWhenEmpty;
    }

    public Status tick(Set<String> holdersInside) {
        if (won) return Status.WON;
        if (holdersInside.isEmpty()) {
            if (drainWhenEmpty && capturer != null) {
                progress--;
                if (progress <= 0) {
                    progress = 0;
                    capturer = null;
                }
            }
            return Status.EMPTY;
        }
        if (holdersInside.size() > 1) return Status.CONTESTED;
        String holder = holdersInside.iterator().next();
        if (!holder.equals(capturer)) {
            capturer = holder;
            progress = 0;
        }
        progress++;
        if (progress >= targetSeconds) {
            won = true;
            return Status.WON;
        }
        return Status.CAPTURING;
    }

    public String capturer() {
        return capturer;
    }

    public int progress() {
        return progress;
    }

    public int target() {
        return targetSeconds;
    }

    public boolean won() {
        return won;
    }
}
