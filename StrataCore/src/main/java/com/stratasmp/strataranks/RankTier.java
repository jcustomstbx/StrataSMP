package com.stratasmp.strataranks;

import org.bukkit.entity.Player;

import java.util.function.Predicate;

/**
 * Paid rank ladder: Tier I to Tier X, each a LuckPerms group (tier1 ... tier10) that inherits the one below.
 * The perk gates only change at three rungs, so those are the values of this enum; {@link #level} is the exact tier.
 */
public enum RankTier {
    NONE, TIER_VI, TIER_VIII, TIER_X;

    public static final int TOP = 10;
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    /** The perk rung for a player: the highest of Tier VI, VIII or X their tier reaches. */
    public static RankTier of(Player player) {
        return fromLevel(level(player));
    }

    /** The player's tier from 0 (none) to 10, read from the {@code group.tierN} permission nodes. */
    public static int level(Player player) {
        return level(group -> {
            String node = "group." + group;
            return player.isPermissionSet(node) && player.hasPermission(node);
        });
    }

    /** Highest tier whose group the predicate says the player is in. */
    static int level(Predicate<String> inGroup) {
        for (int tier = TOP; tier >= 1; tier--) {
            if (inGroup.test(group(tier))) {
                return tier;
            }
        }
        return 0;
    }

    static RankTier fromLevel(int level) {
        if (level >= 10) return TIER_X;
        if (level >= 8) return TIER_VIII;
        if (level >= 6) return TIER_VI;
        return NONE;
    }

    /** The LuckPerms group for a tier, e.g. {@code tier3}. */
    public static String group(int tier) {
        return "tier" + tier;
    }

    /** The tier number for a group id such as {@code tier7}, or 0 when it isn't a tier group. */
    public static int levelOfGroup(String id) {
        for (int tier = 1; tier <= TOP; tier++) {
            if (group(tier).equals(id)) {
                return tier;
            }
        }
        return 0;
    }

    /** Display name for a tier, e.g. {@code Tier VII}; empty for no tier. */
    public static String label(int tier) {
        return tier >= 1 && tier <= TOP ? "Tier " + ROMAN[tier] : "";
    }

    /** Lowest tier number that reaches this rung (0 for none). */
    public int minLevel() {
        return switch (this) {
            case TIER_VI -> 6;
            case TIER_VIII -> 8;
            case TIER_X -> 10;
            default -> 0;
        };
    }

    public String label() {
        return label(minLevel());
    }
}
