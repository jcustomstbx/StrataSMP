package com.stratasmp.stratahub;

import org.bukkit.entity.Boat;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;

/**
 * Boats are meant to be a water vehicle, not a way to skate across land or glide through the air -
 * both let a player cross ground a lot faster than walking, or nudge through a gap a barrier wall
 * would otherwise stop. Every boat move is checked against the vehicle's own vanilla status
 * (Boat.Status, the same value the client uses for the paddle/rocking animation); anything other
 * than being in or under water reverts the move, so a boat on land, ice or in the air simply
 * doesn't go anywhere.
 */
final class BoatWaterOnlyGuard implements Listener {

    private final StrataHub plugin;

    BoatWaterOnlyGuard(StrataHub plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!enabled()) {
            return;
        }
        Vehicle vehicle = event.getVehicle();
        if (!(vehicle instanceof Boat boat)) {
            return;
        }
        if (isWaterborne(boat.getStatus())) {
            return;
        }
        vehicle.teleport(event.getFrom());
    }

    private boolean isWaterborne(Boat.Status status) {
        return status == Boat.Status.IN_WATER
            || status == Boat.Status.UNDER_WATER
            || status == Boat.Status.UNDER_FLOWING_WATER;
    }

    private boolean enabled() {
        return this.plugin.getConfig().getBoolean("boats-water-only", true);
    }
}
