package com.stratasmp.strataeconomy.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public interface Auctions {

    interface Listing {
        UUID id();

        UUID seller();

        String sellerName();

        ItemStack item();

        long price();

        long listedAt();
    }

    List<Listing> all();

    List<Listing> bySeller(UUID seller);

    Listing byId(UUID id);

    /** Removes the item from the seller's hand is the caller's job. */
    Listing list(Player seller, ItemStack item, long price);

    /** @return null on success, otherwise a player-facing error string. */
    String buy(Player buyer, UUID listingId);

    /** @return null on success, otherwise a player-facing error string. */
    String cancel(Player who, UUID listingId);

    /** Removes a listing outright, with no delivery to the seller (used by skin purges). @return true if it existed. */
    boolean adminRemove(UUID listingId);
}
