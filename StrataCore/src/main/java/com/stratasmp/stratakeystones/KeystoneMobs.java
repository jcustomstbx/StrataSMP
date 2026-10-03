package com.stratasmp.stratakeystones;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/** Marks the mobs a keystone run spawns, so other systems (XP, quests, drops) can ignore them. */
public final class KeystoneMobs {
    public static final NamespacedKey KEY = new NamespacedKey("stratasmp", "keystone_mob");

    private KeystoneMobs() {}

    public static boolean isRunMob(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }
}
