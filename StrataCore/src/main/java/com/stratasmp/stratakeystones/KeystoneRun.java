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
    final long deadline;
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
