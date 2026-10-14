package com.stratasmp.stratacombat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CombatTagsTest {
    private final AtomicLong now = new AtomicLong(1_000_000);
    private CombatTags tags;
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tags = new CombatTags(15, now::get);
    }

    @Test
    void taggingStartsACountdown() {
        assertTrue(tags.tag(a), "first tag reports it was new");
        assertTrue(tags.isTagged(a));
        assertEquals(15, tags.secondsLeft(a));
        assertFalse(tags.isTagged(b));
    }

    @Test
    void tagExpiresAfterTheDuration() {
        tags.tag(a);
        now.addAndGet(14_999);
        assertTrue(tags.isTagged(a));
        assertEquals(1, tags.secondsLeft(a));
        now.addAndGet(1);
        assertFalse(tags.isTagged(a));
        assertEquals(0, tags.secondsLeft(a));
    }

    @Test
    void anotherHitRefreshesTheTagWithoutCountingAsNew() {
        tags.tag(a);
        now.addAndGet(10_000);
        assertFalse(tags.tag(a), "already tagged");
        assertEquals(15, tags.secondsLeft(a));
    }

    @Test
    void retaggingAfterExpiryCountsAsNewAgain() {
        tags.tag(a);
        now.addAndGet(20_000);
        assertTrue(tags.tag(a));
    }

    @Test
    void untagClearsImmediately() {
        tags.tag(a);
        tags.untag(a);
        assertFalse(tags.isTagged(a));
    }

    @Test
    void drainExpiredReturnsOnlyFinishedTagsOnce() {
        tags.tag(a);
        now.addAndGet(10_000);
        tags.tag(b);
        now.addAndGet(6_000);
        assertEquals(List.of(a), tags.drainExpired());
        assertTrue(tags.drainExpired().isEmpty());
        assertTrue(tags.isTagged(b));
        assertEquals(List.of(b), tags.tagged());
    }

    @Test
    void nonsenseDurationStillGivesAtLeastOneSecond() {
        CombatTags zero = new CombatTags(0, now::get);
        zero.tag(a);
        assertTrue(zero.isTagged(a));
    }
}
