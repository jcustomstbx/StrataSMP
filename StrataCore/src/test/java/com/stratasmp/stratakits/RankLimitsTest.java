package com.stratasmp.stratakits;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RankLimitsTest {

    @Test
    void readsPerTierCountsAndDefaultsToZero() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("vault-limits.tier1", 1);
        yaml.set("vault-limits.tier10", 10);
        assertEquals(1, RankLimits.count(yaml, "vault-limits", "tier1"));
        assertEquals(10, RankLimits.count(yaml, "vault-limits", "tier10"));
        assertEquals(0, RankLimits.count(yaml, "vault-limits", "tier2"));
        assertEquals(0, RankLimits.count(yaml, "home-limits", "tier1"));
    }

    @Test
    void negativeCountsAreTreatedAsZero() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("vault-limits.tier3", -4);
        assertEquals(0, RankLimits.count(yaml, "vault-limits", "tier3"));
    }

    @Test
    void shippedConfigGivesTierNExactlyNVaults() throws Exception {
        try (var in = RankLimitsTest.class.getResourceAsStream("/modules/StrataKits/config.yml")) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            for (int tier = 1; tier <= 10; tier++) {
                assertEquals(tier, RankLimits.count(yaml, "vault-limits", "tier" + tier));
            }
        }
    }
}
