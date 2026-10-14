package com.stratasmp.stratacombat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Who is in combat and until when. Plain data so it can be tested without a server. */
public final class CombatTags {
    private final Map<UUID, Long> until = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private final long durationMillis;

    public CombatTags(long durationSeconds, LongSupplier clock) {
        this.durationMillis = Math.max(1, durationSeconds) * 1000L;
        this.clock = clock;
    }

    /** Starts or refreshes a tag. Returns true when the player was not tagged before. */
    public boolean tag(UUID id) {
        boolean fresh = !isTagged(id);
        until.put(id, clock.getAsLong() + durationMillis);
        return fresh;
    }

    public boolean isTagged(UUID id) {
        Long end = until.get(id);
        if (end == null) return false;
        if (clock.getAsLong() >= end) {
            until.remove(id, end);
            return false;
        }
        return true;
    }

    public void untag(UUID id) {
        until.remove(id);
    }

    /** Whole seconds left, rounded up; 0 when not tagged. */
    public int secondsLeft(UUID id) {
        Long end = until.get(id);
        if (end == null) return 0;
        long left = end - clock.getAsLong();
        return left <= 0 ? 0 : (int) ((left + 999) / 1000);
    }

    /** Removes and returns everyone whose tag has run out. */
    public List<UUID> drainExpired() {
        long now = clock.getAsLong();
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : until.entrySet()) {
            if (now >= entry.getValue() && until.remove(entry.getKey(), entry.getValue())) {
                expired.add(entry.getKey());
            }
        }
        return expired;
    }

    public List<UUID> tagged() {
        return new ArrayList<>(until.keySet());
    }
}
