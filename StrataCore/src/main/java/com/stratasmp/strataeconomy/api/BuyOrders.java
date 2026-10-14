package com.stratasmp.strataeconomy.api;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

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

    /** All of these run their database work off the main thread; {@code done} runs on the main thread with null on
     *  success, otherwise a player-facing error string. */
    void create(Player buyer, Material material, int amount, long pricePerItem, Consumer<String> done);

    /** Seller fulfills up to {@code amount} of the order from their own inventory. */
    void fulfill(Player seller, UUID orderId, int amount, Consumer<String> done);

    void cancel(Player who, UUID orderId, Consumer<String> done);

    /** Claims one pending delivery into the player's inventory. */
    void claim(Player who, UUID deliveryId, Consumer<String> done);
}
