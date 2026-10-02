package com.stratasmp.strataeconomy.api;

import org.bukkit.Material;

/**
 * Shop price table. StrataElections' elected King nudges {@link #getGlobalMultiplier()}
 * within a staff-approved band; the raw per-material numbers live in prices.yml.
 */
public interface Prices {

    /** Base sell price (what /sell pays), before the multiplier. 0 = not sellable. */
    int baseSellPrice(Material material);

    /** Base buy price (what /shop charges), before the multiplier. 0 = not buyable. */
    int baseBuyPrice(Material material);

    /** Effective sell price with the current multiplier applied. */
    int sellPrice(Material material);

    /** Effective buy price with the current multiplier applied. */
    int buyPrice(Material material);

    boolean canSell(Material material);

    boolean canBuy(Material material);

    double getGlobalMultiplier();

    /** Clamped to [0.5, 2.0]; persisted. */
    void setGlobalMultiplier(double multiplier);
}
