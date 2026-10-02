package com.stratasmp.strataeconomy.shop;

import org.bukkit.Material;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player, per-material daily purchase caps for /shop (see
 * {@code items.<mat>.daily-limit} in the sections/*.yml files). In-memory only
 * - a planned restart clears everyone's count early, which is fine for a soft
 * anti-bulk-buy limit; nothing about it is meant to be dupe-proof.
 */
public final class PurchaseLimits {

    private static final long WINDOW_MILLIS = 24L * 60 * 60 * 1000;

    private record Window(long startMillis, int count) {
    }

    private final Map<UUID, Map<Material, Window>> state = new ConcurrentHashMap<>();

    /** How many more of this material the player can buy right now. Integer.MAX_VALUE if cap <= 0 (unlimited). */
    public int remaining(UUID player, Material material, int cap) {
        if (cap <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, cap - currentWindow(player, material).count());
    }

    /** Call after a purchase actually completes - records `amount` against today's count. */
    public void record(UUID player, Material material, int amount) {
        if (amount <= 0) {
            return;
        }
        Map<Material, Window> byMaterial = state.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
        Window current = currentWindow(player, material);
        byMaterial.put(material, new Window(current.startMillis(), current.count() + amount));
    }

    private Window currentWindow(UUID player, Material material) {
        Map<Material, Window> byMaterial = state.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
        Window window = byMaterial.get(material);
        long now = System.currentTimeMillis();
        if (window == null || now - window.startMillis() >= WINDOW_MILLIS) {
            window = new Window(now, 0);
            byMaterial.put(material, window);
        }
        return window;
    }
}
