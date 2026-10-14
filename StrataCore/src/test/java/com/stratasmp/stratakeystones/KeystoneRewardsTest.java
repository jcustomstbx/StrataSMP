package com.stratasmp.stratakeystones;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KeystoneRewardsTest {
    private final List<String> warnings = new ArrayList<>();

    private KeystoneRewards from(String yaml) throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(yaml);
        return new KeystoneRewards(cfg, warnings::add, m -> 64);
    }

    @Test
    void stratasGrowWithLevel() throws Exception {
        KeystoneRewards r = from("loot:\n  stratas:\n    base: 50\n    per-level: 50\n");
        assertEquals(50, r.stratasFor(1, 1));
        assertEquals(100, r.stratasFor(2, 1));
        assertEquals(500, r.stratasFor(10, 1));
    }

    @Test
    void missingSectionFallsBackToDefaults() {
        KeystoneRewards r = new KeystoneRewards(new YamlConfiguration(), warnings::add, m -> 64);
        assertEquals(KeystoneRewards.DEFAULT_BASE, r.stratasFor(1, 1));
        assertTrue(r.dropsFor(1).isEmpty());
    }

    @Test
    void negativeAndHugeConfigValuesAreSafe() throws Exception {
        KeystoneRewards negative = from("loot:\n  stratas:\n    base: -5\n    per-level: -9\n");
        assertEquals(0, negative.stratasFor(10, 1));
        KeystoneRewards huge = from("loot:\n  stratas:\n    base: 100\n    per-level: 100\n");
        assertTrue(huge.stratasFor(0, 1) >= 0, "a level below 1 never goes negative");
    }

    @Test
    void partyBonusAppliesPerExtraMember() throws Exception {
        KeystoneRewards r = from("loot:\n  stratas:\n    base: 100\n    per-level: 0\n    party-bonus-per-extra-member: 0.1\n");
        assertEquals(100, r.stratasFor(1, 1));
        assertEquals(110, r.stratasFor(1, 2));
        assertEquals(140, r.stratasFor(1, 5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIAMOND 1", "diamond 1", "DIAMOND 1-3", "DIAMOND 2 25%", "DIAMOND 1-3 50", "COOKED_BEEF 64"})
    void validSpecsParse(String spec) {
        assertNotNull(KeystoneRewards.parse(spec), spec);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "DIAMOND", "NOT_A_THING 1", "DIAMOND 0", "DIAMOND -2", "DIAMOND 3-1", "DIAMOND x", "DIAMOND 1 0%",
            "DIAMOND 1 150%", "DIAMOND 1 abc", "DIAMOND 1-2-3", "DIAMOND 1 5% extra", "AIR 1", "LEGACY_STONE 1", "DIAMOND 99999"})
    void badSpecsAreRejected(String spec) {
        assertNull(KeystoneRewards.parse(spec), spec);
    }

    @Test
    void parseReadsAmountRangeAndChance() {
        KeystoneRewards.Drop drop = KeystoneRewards.parse("DIAMOND 1-3 25%");
        assertEquals(Material.DIAMOND, drop.material());
        assertEquals(1, drop.min());
        assertEquals(3, drop.max());
        assertEquals(0.25, drop.chance(), 1e-9);
        assertEquals(1.0, KeystoneRewards.parse("DIAMOND 2").chance(), 1e-9);
    }

    @Test
    void badConfigEntriesWarnAndAreSkipped() throws Exception {
        KeystoneRewards r = from("loot:\n  items:\n    1: [\"DIAMOND 1\", \"nonsense\"]\n");
        assertEquals(1, r.dropsFor(1).size());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("nonsense"));
    }

    @Test
    void guaranteedDropsAlwaysRollAndStayInRange() throws Exception {
        KeystoneRewards r = from("loot:\n  items:\n    1: [\"IRON_INGOT 2-4\"]\n");
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            int total = r.roll(1, 1, random).items().stream().mapToInt(s -> s.amount()).sum();
            assertTrue(total >= 2 && total <= 4, "got " + total);
        }
    }

    @Test
    void chanceDropsLandAboutAsOftenAsConfigured() throws Exception {
        KeystoneRewards r = from("loot:\n  items:\n    1: [\"DIAMOND 1 25%\"]\n");
        Random random = new Random(11);
        int hits = 0;
        for (int i = 0; i < 4000; i++) {
            if (!r.roll(1, 1, random).items().isEmpty()) hits++;
        }
        assertEquals(0.25, hits / 4000.0, 0.03);
    }

    @Test
    void bigAmountsSplitIntoLegalStacks() throws Exception {
        KeystoneRewards r = from("loot:\n  items:\n    1: [\"DIAMOND 150\"]\n");
        var items = r.roll(1, 1, new Random(1)).items();
        assertEquals(150, items.stream().mapToInt(s -> s.amount()).sum());
        assertTrue(items.stream().allMatch(s -> s.amount() <= 64));
    }

    @Test
    void levelsWithNoEntriesGiveOnlyStratas() throws Exception {
        KeystoneRewards r = from("loot:\n  items:\n    1: [\"DIAMOND 1\"]\n");
        KeystoneRewards.Payout payout = r.roll(2, 1, new Random(1));
        assertTrue(payout.items().isEmpty());
        assertTrue(payout.stratas() > 0);
    }

    @Test
    void shippedConfigIsValid() throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        try (var in = getClass().getResourceAsStream("/modules/StrataKeystones/config.yml")) {
            cfg.loadFromString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        KeystoneRewards r = new KeystoneRewards(cfg, warnings::add, m -> 64);
        for (int level = 1; level <= 10; level++) {
            assertFalse(r.dropsFor(level).isEmpty(), "level " + level + " has loot");
            assertTrue(r.stratasFor(level, 1) > 0);
        }
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertTrue(r.stratasFor(10, 1) > r.stratasFor(1, 1));
    }
}
