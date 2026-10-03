package com.stratasmp.stratakeystones;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;

final class KeystoneRun {
    final UUID owner;
    final int level;
    final Location origin;
    final Set<UUID> players = new HashSet<>();
    final Set<UUID> alive = new HashSet<>();
    /** Party members who actually hurt a run mob; only they (and the opener) are paid when the run is cleared. */
    final Set<UUID> contributors = new HashSet<>();
    final long deadline;
    /** Mobs the server can no longer find, with when that started; briefly unloaded or genuinely gone. */
    final java.util.Map<UUID, Long> missingSince = new java.util.HashMap<>();
    /** The level held back in pending.yml while the run is open, so a crash can't eat the keystone. */
    int reserved;
    int wave; // 0 until the first wave spawns
    long nextWaveAt;

    KeystoneRun(UUID owner, int level, Location origin, long deadline) {
        this.owner = owner;
        this.level = level;
        this.origin = origin;
        this.deadline = deadline;
        this.players.add(owner);
    }
}
