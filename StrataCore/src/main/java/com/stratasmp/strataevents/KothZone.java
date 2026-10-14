package com.stratasmp.strataevents;

/** A capture zone: a vertical cylinder around a centre block. */
public record KothZone(String name, String world, double x, double y, double z, double radius, double halfHeight) {

    /** True when the point is inside the cylinder. */
    public boolean contains(String worldName, double px, double py, double pz) {
        if (!world.equals(worldName)) return false;
        if (Math.abs(py - y) > halfHeight) return false;
        double dx = px - x;
        double dz = pz - z;
        return dx * dx + dz * dz <= radius * radius;
    }
}
