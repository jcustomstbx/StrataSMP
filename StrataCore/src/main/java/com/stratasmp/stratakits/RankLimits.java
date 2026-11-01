package com.stratasmp.stratakits;

import org.bukkit.configuration.ConfigurationSection;
import com.stratasmp.stratacore.StrataModule;

/**
 * Home and personal-vault allowances per tier, shown on the rank menu. They come from this module's config
 * ({@code vault-limits} and {@code home-limits}, keyed tier1 ... tier10). The vault plugin enforces the same
 * numbers through LuckPerms {@code playervaults.amount.N} on each tier group, so keep the two in step.
 */
final class RankLimits {

    private final StrataModule plugin;

    RankLimits(StrataModule plugin) {
        this.plugin = plugin;
    }

    void reload() {
        // read straight from the module config on every call, so a /kit reload is all that's needed
    }

    /** 0 when the config has no entry for that tier. */
    int homes(String rank) {
        return count(plugin.getConfig(), "home-limits", rank);
    }

    int vaults(String rank) {
        return count(plugin.getConfig(), "vault-limits", rank);
    }

    static int count(ConfigurationSection config, String section, String rank) {
        return Math.max(0, config.getInt(section + "." + rank, 0));
    }
}
