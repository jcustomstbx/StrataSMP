package com.stratasmp.stratavaults;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class VaultLimitsTest {

    /** Vaults per tier carried over from the RankEssentials config (monarch ... regal map to tier1 ... tier10). */
    private static final int[] EXPECTED = {3, 4, 5, 6, 7, 7, 8, 8, 10, 10};

    private static YamlConfiguration shipped() throws Exception {
        try (var in = VaultLimitsTest.class.getResourceAsStream("/modules/StrataVaults/config.yml")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void shippedConfigMapsEachTierToItsRankEssentialsSlots() throws Exception {
        YamlConfiguration config = shipped();
        for (int tier = 1; tier <= 10; tier++) {
            assertEquals(EXPECTED[tier - 1], VaultLimits.max(config, tier, false), "tier " + tier);
        }
    }

    @Test
    void noTierGetsTheDefaultAndOpsGetTheOpAllowance() throws Exception {
        YamlConfiguration config = shipped();
        assertEquals(0, VaultLimits.max(config, 0, false));
        assertEquals(10, VaultLimits.max(config, 0, true));
        assertEquals(10, VaultLimits.max(config, 3, true));
    }

    @Test
    void higherTiersNeverGetFewerVaultsThanLowerOnes() throws Exception {
        YamlConfiguration config = shipped();
        for (int tier = 2; tier <= 10; tier++) {
            assertEquals(true, VaultLimits.max(config, tier, false) >= VaultLimits.max(config, tier - 1, false), "tier " + tier);
        }
    }

    @Test
    void missingOrBadEntriesNeverGoNegative() {
        YamlConfiguration config = new YamlConfiguration();
        assertEquals(0, VaultLimits.max(config, 5, false));
        config.set("vault-limits.default", -3);
        config.set("vault-limits.tier2", -1);
        assertEquals(0, VaultLimits.max(config, 2, false));
    }

    @Test
    void vaultNumbersMustBeWholeAndInRange() {
        assertEquals(3, VaultLimits.parseNumber("3", 5));
        assertEquals(5, VaultLimits.parseNumber(" 5 ", 5));
        assertEquals(-1, VaultLimits.parseNumber("6", 5));
        assertEquals(-1, VaultLimits.parseNumber("0", 5));
        assertEquals(-1, VaultLimits.parseNumber("-1", 5));
        assertEquals(-1, VaultLimits.parseNumber("two", 5));
        assertEquals(-1, VaultLimits.parseNumber("", 5));
    }
}
