package com.stratasmp.stratakeystones;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/** Marks the mobs a keystone run spawns, so other systems (XP, quests, drops) can ignore them. */
public final class KeystoneMobs {
    public static final NamespacedKey KEY = new NamespacedKey("stratasmp", "keystone_mob");

    /** The opener of the run this mob belongs to (a UUID string). */
    public static final NamespacedKey RUN_OWNER = new NamespacedKey("stratasmp", "keystone_run");

    private KeystoneMobs() {}

    public static void tag(Entity entity, java.util.UUID owner) {
        entity.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(RUN_OWNER, PersistentDataType.STRING, owner.toString());
    }

    public static java.util.UUID owner(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(RUN_OWNER, PersistentDataType.STRING);
        try {
            return raw == null ? null : java.util.UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isRunMob(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }
}
