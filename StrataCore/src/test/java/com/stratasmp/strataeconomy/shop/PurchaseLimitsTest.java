package com.stratasmp.strataeconomy.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class PurchaseLimitsTest {
    private final PurchaseLimits limits = new PurchaseLimits();
    private final UUID player = UUID.randomUUID();

    @Test
    void anUncappedItemIsUnlimited() {
        assertEquals(Integer.MAX_VALUE, limits.remaining(player, Material.STONE, 0));
        assertEquals(Integer.MAX_VALUE, limits.remaining(player, Material.STONE, -1));
    }

    @Test
    void purchasesCountDownTheDailyCap() {
        assertEquals(5, limits.remaining(player, Material.WHEAT, 5));
        limits.record(player, Material.WHEAT, 2);
        assertEquals(3, limits.remaining(player, Material.WHEAT, 5));
        limits.record(player, Material.WHEAT, 3);
        assertEquals(0, limits.remaining(player, Material.WHEAT, 5));
    }

    @Test
    void overspendingNeverGoesNegative() {
        limits.record(player, Material.WHEAT, 9);
        assertEquals(0, limits.remaining(player, Material.WHEAT, 5));
    }

    @Test
    void limitsArePerPlayerAndPerMaterial() {
        limits.record(player, Material.WHEAT, 5);
        assertEquals(5, limits.remaining(UUID.randomUUID(), Material.WHEAT, 5));
        assertEquals(5, limits.remaining(player, Material.CARROT, 5));
    }

    @Test
    void emptyAndNegativeRecordsAreIgnored() {
        limits.record(player, Material.WHEAT, 0);
        limits.record(player, Material.WHEAT, -4);
        assertEquals(5, limits.remaining(player, Material.WHEAT, 5));
    }
}
