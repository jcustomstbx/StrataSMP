package com.stratasmp.strataranks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class RankTierTest {

    @Test
    void noTierGroupMeansNone() {
        assertEquals(0, RankTier.level(group -> false));
        assertEquals(RankTier.NONE, RankTier.fromLevel(0));
    }

    @Test
    void highestTierGroupWinsBecauseGroupsInherit() {
        // a Tier V player inherits tier1..tier5 in LuckPerms
        assertEquals(5, RankTier.level(Set.of("tier1", "tier2", "tier3", "tier4", "tier5")::contains));
        assertEquals(10, RankTier.level(Set.of("tier1", "tier10")::contains));
    }

    @Test
    void unrelatedGroupsAreIgnored() {
        assertEquals(0, RankTier.level(Set.of("default", "admin", "owner", "tier0", "tier11", "regal")::contains));
    }

    @Test
    void perkRungsStartAtSixEightAndTen() {
        assertEquals(RankTier.NONE, RankTier.fromLevel(5));
        assertEquals(RankTier.TIER_VI, RankTier.fromLevel(6));
        assertEquals(RankTier.TIER_VI, RankTier.fromLevel(7));
        assertEquals(RankTier.TIER_VIII, RankTier.fromLevel(8));
        assertEquals(RankTier.TIER_VIII, RankTier.fromLevel(9));
        assertEquals(RankTier.TIER_X, RankTier.fromLevel(10));
    }

    @Test
    void rungOrderMatchesTheirOrdinalsSoComparisonsHold() {
        assertEquals(true, RankTier.TIER_VIII.ordinal() > RankTier.TIER_VI.ordinal());
        assertEquals(true, RankTier.TIER_X.ordinal() > RankTier.TIER_VIII.ordinal());
    }

    @Test
    void groupNamesAndLabelsRoundTrip() {
        for (int tier = 1; tier <= RankTier.TOP; tier++) {
            assertEquals(tier, RankTier.levelOfGroup(RankTier.group(tier)));
        }
        assertEquals("Tier I", RankTier.label(1));
        assertEquals("Tier IV", RankTier.label(4));
        assertEquals("Tier IX", RankTier.label(9));
        assertEquals("Tier X", RankTier.label(10));
        assertEquals("", RankTier.label(0));
        assertEquals("", RankTier.label(11));
        assertEquals(0, RankTier.levelOfGroup("regal"));
    }
}
