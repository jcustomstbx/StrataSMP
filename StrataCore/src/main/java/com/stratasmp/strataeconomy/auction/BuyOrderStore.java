package com.stratasmp.strataeconomy.auction;

import com.stratasmp.strataeconomy.StrataEconomy;
import com.stratasmp.strataeconomy.api.BuyOrders;
import com.stratasmp.strataeconomy.currency.Database;
import com.stratasmp.strataranks.RankTier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class BuyOrderStore implements BuyOrders {

    private final StrataEconomy plugin;
    private final Database db;
    private final ConcurrentHashMap<UUID, OrderImpl> orders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, DeliveryImpl> deliveries = new ConcurrentHashMap<>();
    // players with a save in flight, orders a database write is working on, deliveries given out but not yet deleted
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<UUID> handedOut = ConcurrentHashMap.newKeySet();
    private final Set<UUID> deleting = ConcurrentHashMap.newKeySet();

    private static final String WAIT = "Please wait a moment, your last order action is still being saved.";

    private final int maxPerPlayer;
    private final int maxAmount;
    private final long minPrice;
    private final long maxPrice;

    public BuyOrderStore(StrataEconomy plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
        var cfg = plugin.getConfig();
        this.maxPerPlayer = cfg.getInt("buyorders.max-per-player", 5);
        this.maxAmount = cfg.getInt("buyorders.max-amount", 2304);
        this.minPrice = cfg.getLong("buyorders.min-price", 1);
        this.maxPrice = cfg.getLong("buyorders.max-price", 100_000_000L);
        loadAll();
    }

    private void loadAll() {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, buyer, buyer_name, material, amount, price_per_item, created_at FROM stratas_buy_orders");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Material m = Material.matchMaterial(rs.getString("material"));
                if (m == null) {
                    continue;
                }
                OrderImpl o = new OrderImpl(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("buyer")),
                        rs.getString("buyer_name"),
                        m, rs.getInt("amount"), rs.getLong("price_per_item"), rs.getLong("created_at"));
                orders.put(o.id, o);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load buy orders: " + e.getMessage());
        }
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, buyer, material, amount, delivered_at FROM stratas_buy_order_deliveries");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Material m = Material.matchMaterial(rs.getString("material"));
                if (m == null) {
                    continue;
                }
                DeliveryImpl d = new DeliveryImpl(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("buyer")),
                        m, rs.getInt("amount"), rs.getLong("delivered_at"));
                deliveries.put(d.id, d);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load buy order deliveries: " + e.getMessage());
        }
        plugin.getLogger().info("Buy orders: loaded " + orders.size() + " order(s), " + deliveries.size() + " pending delivery(ies).");
    }

    public int maxPerPlayer() {
        return maxPerPlayer;
    }

    public int maxPerPlayer(Player player) {
        return maxPerPlayer + switch (RankTier.of(player)) {
            case ORNATE -> 1;
            case REGAL -> 4;
            default -> 0;
        };
    }

    public int maxAmount() {
        return maxAmount;
    }

    public long minPrice() {
        return minPrice;
    }

    public long maxPrice() {
        return maxPrice;
    }

    @Override
    public List<Order> all() {
        return orders.values().stream()
                .sorted((a, b) -> Long.compare(b.createdAt, a.createdAt))
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> byBuyer(UUID buyer) {
        return orders.values().stream()
                .filter(o -> o.buyer.equals(buyer))
                .collect(Collectors.toList());
    }

    @Override
    public Order byId(UUID id) {
        return orders.get(id);
    }

    @Override
    public List<Delivery> deliveriesFor(UUID buyer) {
        return deliveries.values().stream()
                .filter(d -> d.buyer.equals(buyer))
                .sorted((a, b) -> Long.compare(a.deliveredAt, b.deliveredAt))
                .collect(Collectors.toList());
    }

    @Override
    public void create(Player buyer, Material material, int amount, long pricePerItem, Consumer<String> done) {
        if (!material.isItem() || material.isAir()) {
            done.accept(plugin.msgRaw("bo-bad-material"));
            return;
        }
        if (amount <= 0 || amount > maxAmount) {
            done.accept(plugin.msgRaw("bo-bad-amount"));
            return;
        }
        if (pricePerItem < minPrice || pricePerItem > maxPrice) {
            done.accept(plugin.msgRaw("bo-bad-price"));
            return;
        }
        UUID buyerId = buyer.getUniqueId();
        if (!buyer.hasPermission("strataeconomy.admin") && byBuyer(buyerId).size() >= maxPerPlayer(buyer)) {
            done.accept(plugin.msgRaw("bo-max-orders"));
            return;
        }
        if (!busy.add(buyerId)) {
            done.accept(WAIT);
            return;
        }
        long total = pricePerItem * amount;
        OrderImpl o = new OrderImpl(UUID.randomUUID(), buyerId, buyer.getName(),
                material, amount, pricePerItem, System.currentTimeMillis());
        plugin.offload(() -> plugin.stratas().applyAtomicDeltas(Map.of(buyerId, -total), c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO stratas_buy_orders (id, buyer, buyer_name, material, amount, price_per_item, created_at) VALUES (?,?,?,?,?,?,?)")) {
                ps.setString(1, o.id.toString());
                ps.setString(2, o.buyer.toString());
                ps.setString(3, o.buyerName);
                ps.setString(4, o.material.name());
                ps.setInt(5, o.amount);
                ps.setLong(6, o.pricePerItem);
                ps.setLong(7, o.createdAt);
                ps.executeUpdate();
            }
        }), (committed, failure) -> {
            busy.remove(buyerId);
            if (failure != null) {
                plugin.getLogger().severe("Buy order transaction failed: " + failure.getMessage());
                done.accept("The buy order could not be saved. No Stratas were charged; please try again.");
                return;
            }
            if (!committed) {
                done.accept(plugin.msgRaw("shop-cant-afford"));
                return;
            }
            orders.put(o.id, o);
            done.accept(null);
        });
    }

    @Override
    public void fulfill(Player seller, UUID orderId, int requestedAmount, Consumer<String> done) {
        OrderImpl o = orders.get(orderId);
        if (o == null || inFlight.contains(orderId)) {
            done.accept(plugin.msgRaw("bo-gone"));
            return;
        }
        UUID sellerId = seller.getUniqueId();
        if (o.buyer.equals(sellerId)) {
            done.accept(plugin.msgRaw("bo-own-order"));
            return;
        }
        int have = countMaterial(seller.getInventory(), o.material);
        int actual = Math.min(requestedAmount, Math.min(have, o.amount));
        if (actual <= 0) {
            done.accept(plugin.msgRaw("bo-no-items"));
            return;
        }
        if (!busy.add(sellerId)) {
            done.accept(WAIT);
            return;
        }
        if (!inFlight.add(orderId)) {
            busy.remove(sellerId);
            done.accept(plugin.msgRaw("bo-gone"));
            return;
        }

        long payout = o.pricePerItem * actual;
        int remaining = o.amount - actual;
        // the items leave the inventory now so they can't be moved while the save is in flight
        removeMaterial(seller.getInventory(), o.material, actual);
        final int taken = actual;
        DeliveryImpl delivery = new DeliveryImpl(UUID.randomUUID(), o.buyer, o.material, actual,
                System.currentTimeMillis());
        Map<UUID, Long> deltas = Map.of(sellerId, payout);
        plugin.offload(() -> plugin.stratas().applyAtomicDeltas(deltas, c -> {
            if (remaining <= 0) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM stratas_buy_orders WHERE id=?")) {
                    ps.setString(1, o.id.toString());
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Buy order was already removed");
                    }
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement("UPDATE stratas_buy_orders SET amount=? WHERE id=?")) {
                    ps.setInt(1, remaining);
                    ps.setString(2, o.id.toString());
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Buy order was already removed");
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO stratas_buy_order_deliveries (id, buyer, material, amount, delivered_at) VALUES (?,?,?,?,?)")) {
                ps.setString(1, delivery.id.toString());
                ps.setString(2, delivery.buyer.toString());
                ps.setString(3, delivery.material.name());
                ps.setInt(4, delivery.amount);
                ps.setLong(5, delivery.deliveredAt);
                ps.executeUpdate();
            }
        }), (committed, failure) -> {
            inFlight.remove(orderId);
            busy.remove(sellerId);
            if (failure != null || !committed) {
                if (failure != null) {
                    plugin.getLogger().severe("Buy order fulfillment transaction failed: " + failure.getMessage());
                }
                returnItems(seller, o.material, taken);
                done.accept(failure != null
                        ? "The order could not be completed. Your items were returned; please try again."
                        : plugin.msgRaw("shop-cant-afford"));
                return;
            }
            if (seller.isOnline()) {
                seller.saveData();
            }
            if (remaining <= 0) {
                orders.remove(o.id, o);
            } else {
                orders.put(o.id, new OrderImpl(o.id, o.buyer, o.buyerName, o.material, remaining,
                        o.pricePerItem, o.createdAt));
            }
            deliveries.put(delivery.id, delivery);
            deliverOnline(delivery);

            if (seller.isOnline()) {
                seller.sendMessage(plugin.msg().get("bo-fulfilled", Map.of(
                        "amount", String.valueOf(taken),
                        "item", o.material.name().toLowerCase().replace('_', ' '),
                        "price", plugin.money(payout))));
            }
            Player buyerOnline = Bukkit.getPlayer(o.buyer);
            if (buyerOnline != null) {
                buyerOnline.sendMessage(plugin.msg().get("bo-order-filled", Map.of(
                        "player", seller.getName(),
                        "amount", String.valueOf(taken),
                        "item", o.material.name().toLowerCase().replace('_', ' '))));
            }
            done.accept(null);
        });
    }

    /** Gives back items taken for a save that failed; whatever doesn't fit is dropped at the player's feet. */
    private void returnItems(Player seller, Material material, int amount) {
        if (!seller.isOnline()) {
            plugin.getLogger().severe("Could not return " + amount + "x " + material + " to " + seller.getName()
                    + " (" + seller.getUniqueId() + ") after a failed buy order fill; they logged out first.");
            return;
        }
        ItemStack stack = new ItemStack(material, amount);
        for (ItemStack left : seller.getInventory().addItem(stack).values()) {
            seller.getWorld().dropItem(seller.getLocation(), left);
        }
    }

    @Override
    public void cancel(Player who, UUID orderId, Consumer<String> done) {
        OrderImpl o = orders.get(orderId);
        if (o == null || inFlight.contains(orderId)) {
            done.accept(plugin.msgRaw("bo-gone"));
            return;
        }
        if (!o.buyer.equals(who.getUniqueId()) && !who.hasPermission("strataeconomy.admin")) {
            done.accept(plugin.msgRaw("bo-not-yours"));
            return;
        }
        UUID whoId = who.getUniqueId();
        if (!busy.add(whoId)) {
            done.accept(WAIT);
            return;
        }
        if (!inFlight.add(orderId)) {
            busy.remove(whoId);
            done.accept(plugin.msgRaw("bo-gone"));
            return;
        }
        plugin.offload(() -> plugin.stratas().applyAtomicDeltas(Map.of(o.buyer, o.pricePerItem * o.amount), c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM stratas_buy_orders WHERE id=?")) {
                ps.setString(1, o.id.toString());
                if (ps.executeUpdate() != 1) {
                    throw new SQLException("Buy order was already removed");
                }
            }
        }), (committed, failure) -> {
            inFlight.remove(orderId);
            busy.remove(whoId);
            if (failure != null) {
                plugin.getLogger().severe("Buy order cancellation transaction failed: " + failure.getMessage());
            }
            if (failure != null || !committed) {
                done.accept("The refund could not be saved. Please try again.");
                return;
            }
            orders.remove(o.id, o);
            done.accept(null);
        });
    }

    @Override
    public void claim(Player who, UUID deliveryId, Consumer<String> done) {
        DeliveryImpl d = deliveries.get(deliveryId);
        if (d == null || handedOut.contains(deliveryId)) {
            done.accept(plugin.msgRaw("bo-gone"));
            return;
        }
        if (!d.buyer.equals(who.getUniqueId())) {
            done.accept(plugin.msgRaw("bo-not-yours"));
            return;
        }
        if (!giveOut(who, d)) {
            done.accept(plugin.msgRaw("ah-inventory-full"));
            return;
        }
        done.accept(null);
    }

    /** All-or-nothing capacity check so a claim never partially lands and loses the rest. */
    private boolean canFullyFit(PlayerInventory inv, ItemStack item) {
        int remaining = item.getAmount();
        int maxStack = item.getMaxStackSize();
        for (ItemStack it : inv.getStorageContents()) {
            if (it == null) {
                remaining -= maxStack;
            } else if (it.isSimilar(item)) {
                remaining -= Math.max(0, maxStack - it.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return remaining <= 0;
    }

    /* ---- internal ---- */

    private void deliverOnline(DeliveryImpl delivery) {
        Player online = Bukkit.getPlayer(delivery.buyer);
        if (online == null || handedOut.contains(delivery.id)) {
            return;
        }
        giveOut(online, delivery);
    }

    /**
     * Puts the delivery in the inventory under a marker, then deletes its row in the background and clears the marker.
     * Handing it over first means a crash can't lose it; the marker means a retry can't duplicate it.
     */
    private boolean giveOut(Player player, DeliveryImpl delivery) {
        ItemStack item = DeliveryMarker.mark(plugin, new ItemStack(delivery.material, delivery.amount), delivery.id);
        if (!canFullyFit(player.getInventory(), item)) {
            return false;
        }
        if (!player.getInventory().addItem(item).isEmpty()) {
            return false;
        }
        handedOut.add(delivery.id);
        player.saveData();
        scheduleDelete(player.getUniqueId(), delivery);
        return true;
    }

    private void scheduleDelete(UUID playerId, DeliveryImpl delivery) {
        if (!deleting.add(delivery.id)) {
            return;
        }
        plugin.offload(() -> {
            deleteDeliveryRow(delivery.id);
            return true;
        }, (ok, failure) -> {
            deleting.remove(delivery.id);
            if (failure != null) {
                // stays handed out, so it can't be given again this session; the next join retries the delete
                plugin.getLogger().severe("Buy order delivery acknowledgement failed: " + failure.getMessage());
                return;
            }
            deliveries.remove(delivery.id, delivery);
            handedOut.remove(delivery.id);
            Player online = Bukkit.getPlayer(playerId);
            if (online != null) {
                DeliveryMarker.clearFromInventory(plugin, online, delivery.id);
                online.saveData();
            }
        });
    }

    public void claimPendingDeliveries(Player player) {
        reconcileMarkers(player);
        for (DeliveryImpl delivery : deliveries.values()) {
            if (delivery.buyer.equals(player.getUniqueId()) && !handedOut.contains(delivery.id)
                    && !DeliveryMarker.present(player, plugin, delivery.id)) {
                deliverOnline(delivery);
            }
        }
    }

    private void reconcileMarkers(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        boolean changed = false;
        for (ItemStack item : contents) {
            UUID id = DeliveryMarker.id(plugin, item);
            if (id == null) continue;
            DeliveryImpl delivery = deliveries.get(id);
            if (delivery != null) {
                handedOut.add(id);
                scheduleDelete(player.getUniqueId(), delivery);
                continue;
            }
            changed |= DeliveryMarker.clear(plugin, item);
        }
        if (changed) {
            player.getInventory().setStorageContents(contents);
            player.saveData();
        }
    }

    private int countMaterial(PlayerInventory inv, Material material) {
        int count = 0;
        for (ItemStack it : inv.getStorageContents()) {
            if (it != null && it.getType() == material && !hasCustomModel(it)) {
                count += it.getAmount();
            }
        }
        return count;
    }

    private void removeMaterial(PlayerInventory inv, Material material, int amount) {
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType() != material || hasCustomModel(it)) {
                continue;
            }
            int take = Math.min(amount, it.getAmount());
            it.setAmount(it.getAmount() - take);
            contents[i] = it.getAmount() <= 0 ? null : it;
            amount -= take;
        }
        inv.setStorageContents(contents);
    }

    /**
     * Only untouched stacks can fill an order: the buyer is handed a brand-new item, so anything enchanted, named,
     * damaged, skinned or holding contents (shulker box, bundle, potion) would be silently reset - a free repair or
     * curse-strip - or lose what was inside it.
     */
    private boolean hasCustomModel(ItemStack it) {
        return !it.isSimilar(new ItemStack(it.getType()));
    }

    private void deleteDeliveryRow(UUID id) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM stratas_buy_order_deliveries WHERE id=?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        }
    }

    private record OrderImpl(UUID id, UUID buyer, String buyerName, Material material, int amount, long pricePerItem, long createdAt)
            implements Order {
        @Override
        public int amountRemaining() {
            return amount;
        }
    }

    private record DeliveryImpl(UUID id, UUID buyer, Material material, int amount, long deliveredAt) implements Delivery {
    }
}
