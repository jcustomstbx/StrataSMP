package com.stratasmp.strataeconomy.api;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public interface BuyOrders {

    interface Order {
        UUID id();

        UUID buyer();

        String buyerName();

        Material material();

        int amountRemaining();

        long pricePerItem();

        long createdAt();
    }

    interface Delivery {
        UUID id();

        Material material();

        int amount();

        long deliveredAt();
    }

    List<Order> all();

    List<Order> byBuyer(UUID buyer);

    Order byId(UUID id);

    List<Delivery> deliveriesFor(UUID buyer);

    /** @return null on success, otherwise a player-facing error string. */
    String create(Player buyer, Material material, int amount, long pricePerItem);

    /** Seller fulfills up to {@code amount} of the order from their own inventory. @return null on success, otherwise a player-facing error string. */
    String fulfill(Player seller, UUID orderId, int amount);

    /** @return null on success, otherwise a player-facing error string. */
    String cancel(Player who, UUID orderId);

    /** Claims one pending delivery into the player's inventory. @return null on success, otherwise a player-facing error string. */
    String claim(Player who, UUID deliveryId);
}
