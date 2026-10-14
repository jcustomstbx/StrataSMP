package com.stratasmp.strataeconomy.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

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

    /**
     * Saves the listing off the main thread. Taking the item from the seller is the caller's job and must happen
     * first. {@code done} runs on the main thread with the listing, or null if it could not be saved.
     */
    void list(Player seller, ItemStack item, long price, Consumer<Listing> done);

    /** {@code done} runs on the main thread with null on success, otherwise a player-facing error string. */
    void buy(Player buyer, UUID listingId, Consumer<String> done);

    /** {@code done} runs on the main thread with null on success, otherwise a player-facing error string. */
    void cancel(Player who, UUID listingId, Consumer<String> done);

    /** Removes a listing outright, with no delivery to the seller (used by skin purges). @return true if it existed. */
    boolean adminRemove(UUID listingId);
}
