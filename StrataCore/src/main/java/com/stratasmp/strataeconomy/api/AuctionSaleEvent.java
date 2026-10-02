package com.stratasmp.strataeconomy.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired the moment an auction listing sells, before the seller is paid.
 * StrataElections listens and sets a tax based on the current King's policy;
 * the auction house then pays the seller {@code price - tax}.
 */
public class AuctionSaleEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID seller;
    private final Player buyer;
    private final ItemStack item;
    private final long price;
    private long tax;

    public AuctionSaleEvent(UUID seller, Player buyer, ItemStack item, long price) {
        this.seller = seller;
        this.buyer = buyer;
        this.item = item;
        this.price = price;
    }

    public UUID getSeller() {
        return seller;
    }

    public Player getBuyer() {
        return buyer;
    }

    public ItemStack getItem() {
        return item.clone();
    }

    public long getPrice() {
        return price;
    }

    public long getTax() {
        return tax;
    }

    /** Clamped to [0, price] by the auction house. */
    public void setTax(long tax) {
        this.tax = tax;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
