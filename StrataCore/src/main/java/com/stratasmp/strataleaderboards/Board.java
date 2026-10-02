package com.stratasmp.strataleaderboards;

import java.util.UUID;

/**
 * A placed board: what it shows, where it floats and which entity is drawing it. {@code yaw} is the direction
 * it faces (a FIXED-billboard display, flat against a wall, rather than always turning to face the viewer).
 */
public record Board(String id, BoardType type, String world, double x, double y, double z, float scale, float yaw,
                     UUID entityId) {

    public Board withEntity(UUID newEntityId) {
        return new Board(id, type, world, x, y, z, scale, yaw, newEntityId);
    }

    public Board movedTo(String newWorld, double newX, double newY, double newZ, float newYaw) {
        return new Board(id, type, newWorld, newX, newY, newZ, scale, newYaw, entityId);
    }

    public Board resized(float newScale) {
        return new Board(id, type, world, x, y, z, newScale, yaw, entityId);
    }
}
