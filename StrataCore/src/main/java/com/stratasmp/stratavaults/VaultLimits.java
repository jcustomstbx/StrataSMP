package com.stratasmp.stratavaults;

import org.bukkit.configuration.ConfigurationSection;

/** How many personal vaults a player may open, read from the {@code vault-limits} section of the module config. */
final class VaultLimits {

    private VaultLimits() {
    }

    /**
     * @param tier 0 for no paid tier, otherwise 1 to 10
     * @param op   operators get the {@code op} entry whatever their tier
     */
    static int max(ConfigurationSection config, int tier, boolean op) {
        ConfigurationSection limits = config.getConfigurationSection("vault-limits");
        if (limits == null) {
            return 0;
        }
        if (op) {
            return Math.max(0, limits.getInt("op", 0));
        }
        int base = Math.max(0, limits.getInt("default", 0));
        if (tier < 1) {
            return base;
        }
        return Math.max(base, Math.max(0, limits.getInt("tier" + tier, 0)));
    }

    /** A vault number from player input, or -1 when it isn't a whole number from 1 to {@code max}. */
    static int parseNumber(String text, int max) {
        try {
            int number = Integer.parseInt(text.trim());
            return number >= 1 && number <= max ? number : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
