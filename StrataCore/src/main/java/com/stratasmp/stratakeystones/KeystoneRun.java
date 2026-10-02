package com.stratasmp.stratakeystones;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;

final class KeystoneRun {
    final UUID player;
    final int level;
    final Location origin;
    final Set<UUID> alive = new HashSet<>();
    final long deadline;
    int wave; // 0 until the first wave spawns
    long nextWaveAt;

    KeystoneRun(UUID player, int level, Location origin, long deadline) {
        this.player = player;
        this.level = level;
        this.origin = origin;
        this.deadline = deadline;
    }
}
