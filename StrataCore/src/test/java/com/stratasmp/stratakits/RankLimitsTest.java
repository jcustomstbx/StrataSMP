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
}
