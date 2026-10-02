package com.stratasmp.strataranks;

import org.bukkit.entity.Player;

/** Paid rank ladder, independent of the existing progression ranks. */
public enum RankTier {
    NONE, IMPERIAL, ORNATE, REGAL;

    public static RankTier of(Player player) {
        if (hasGroup(player, "regal")) return REGAL;
        if (hasGroup(player, "ornate")) return ORNATE;
        if (hasGroup(player, "imperial")) return IMPERIAL;
        return NONE;
    }

    private static boolean hasGroup(Player player, String group) {
        String node = "group." + group;
        return player.isPermissionSet(node) && player.hasPermission(node);
    }
}
