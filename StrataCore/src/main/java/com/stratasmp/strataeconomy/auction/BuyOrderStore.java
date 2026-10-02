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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class BuyOrderStore implements BuyOrders {

    private final StrataEconomy plugin;
    private final Database db;
    private final ConcurrentHashMap<UUID, OrderImpl> orders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, DeliveryImpl> deliveries = new ConcurrentHashMap<>();

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
    public String create(Player buyer, Material material, int amount, long pricePerItem) {
        if (!material.isItem() || material.isAir()) {
            return plugin.msgRaw("bo-bad-material");
        }
        if (amount <= 0 || amount > maxAmount) {
            return plugin.msgRaw("bo-bad-amount");
        }
        if (pricePerItem < minPrice || pricePerItem > maxPrice) {
            return plugin.msgRaw("bo-bad-price");
        }
        if (!buyer.hasPermission("strataeconomy.admin") && byBuyer(buyer.getUniqueId()).size() >= maxPerPlayer(buyer)) {
            return plugin.msgRaw("bo-max-orders");
        }
        long total = pricePerItem * amount;
        OrderImpl o = new OrderImpl(UUID.randomUUID(), buyer.getUniqueId(), buyer.getName(),
                material, amount, pricePerItem, System.currentTimeMillis());
        try {
            boolean committed = plugin.stratas().applyAtomicDeltas(Map.of(buyer.getUniqueId(), -total), c -> {
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
            });
            if (!committed) {
                return plugin.msgRaw("shop-cant-afford");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Buy order transaction failed: " + e.getMessage());
            return "The buy order could not be saved. No Stratas were charged; please try again.";
        }
        orders.put(o.id, o);
        return null;
    }

    @Override
    public String fulfill(Player seller, UUID orderId, int requestedAmount) {
        OrderImpl o = orders.get(orderId);
        if (o == null) {
            return plugin.msgRaw("bo-gone");
        }
        if (o.buyer.equals(seller.getUniqueId())) {
            return plugin.msgRaw("bo-own-order");
        }
        int have = countMaterial(seller.getInventory(), o.material);
        int actual = Math.min(requestedAmount, Math.min(have, o.amount));
        if (actual <= 0) {
            return plugin.msgRaw("bo-no-items");
        }

        long payout = o.pricePerItem * actual;
        int remaining = o.amount - actual;
        ItemStack[] originalInventory = seller.getInventory().getStorageContents();
        removeMaterial(seller.getInventory(), o.material, actual);
        DeliveryImpl delivery = new DeliveryImpl(UUID.randomUUID(), o.buyer, o.material, actual,
                System.currentTimeMillis());
        Map<UUID, Long> deltas = Map.of(seller.getUniqueId(), payout);
        try {
            boolean committed = plugin.stratas().applyAtomicDeltas(deltas, c -> {
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
            });
            if (!committed) {
                seller.getInventory().setStorageContents(originalInventory);
                return plugin.msgRaw("shop-cant-afford");
            }
        } catch (SQLException e) {
            seller.getInventory().setStorageContents(originalInventory);
            plugin.getLogger().severe("Buy order fulfillment transaction failed: " + e.getMessage());
            return "The order could not be completed. Your items were returned; please try again.";
        }
        seller.saveData();
        if (remaining <= 0) {
            orders.remove(o.id, o);
        } else {
            orders.put(o.id, new OrderImpl(o.id, o.buyer, o.buyerName, o.material, remaining,
                    o.pricePerItem, o.createdAt));
        }
        deliveries.put(delivery.id, delivery);
        deliverOnline(delivery);

        seller.sendMessage(plugin.msg().get("bo-fulfilled", Map.of(
                "amount", String.valueOf(actual),
                "item", o.material.name().toLowerCase().replace('_', ' '),
                "price", plugin.money(payout))));
        Player buyerOnline = Bukkit.getPlayer(o.buyer);
        if (buyerOnline != null) {
            buyerOnline.sendMessage(plugin.msg().get("bo-order-filled", Map.of(
                    "player", seller.getName(),
                    "amount", String.valueOf(actual),
                    "item", o.material.name().toLowerCase().replace('_', ' '))));
        }
        return null;
    }

    @Override
    public String cancel(Player who, UUID orderId) {
        OrderImpl o = orders.get(orderId);
        if (o == null) {
            return plugin.msgRaw("bo-gone");
        }
        if (!o.buyer.equals(who.getUniqueId()) && !who.hasPermission("strataeconomy.admin")) {
            return plugin.msgRaw("bo-not-yours");
        }
        try {
            boolean committed = plugin.stratas().applyAtomicDeltas(
                    Map.of(o.buyer, o.pricePerItem * o.amount), c -> {
                        try (PreparedStatement ps = c.prepareStatement("DELETE FROM stratas_buy_orders WHERE id=?")) {
                            ps.setString(1, o.id.toString());
                            if (ps.executeUpdate() != 1) {
                                throw new SQLException("Buy order was already removed");
                            }
                        }
                    });
            if (!committed) {
                return "The refund could not be saved. Please try again.";
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Buy order cancellation transaction failed: " + e.getMessage());
            return "The refund could not be saved. Please try again.";
        }
        orders.remove(o.id, o);
        return null;
    }

    @Override
    public String claim(Player who, UUID deliveryId) {
        DeliveryImpl d = deliveries.get(deliveryId);
        if (d == null) {
            return plugin.msgRaw("bo-gone");
        }
        if (!d.buyer.equals(who.getUniqueId())) {
            return plugin.msgRaw("bo-not-yours");
        }
        ItemStack item = DeliveryMarker.mark(plugin, new ItemStack(d.material, d.amount), d.id);
        if (!canFullyFit(who.getInventory(), item)) {
            return plugin.msgRaw("ah-inventory-full");
        }
        who.getInventory().addItem(item);
        who.saveData();
        try {
            deleteDeliveryRow(d.id);
        } catch (SQLException e) {
            plugin.getLogger().severe("Buy order delivery acknowledgement failed: " + e.getMessage());
            return "The item was delivered, but its receipt could not be saved. Contact staff before claiming again.";
        }
        deliveries.remove(d.id, d);
        DeliveryMarker.clearFromInventory(plugin, who, d.id);
        who.saveData();
        return null;
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
        ItemStack item = DeliveryMarker.mark(plugin, new ItemStack(delivery.material, delivery.amount), delivery.id);
        if (online == null || !canFullyFit(online.getInventory(), item)) {
            return;
        }
        Map<Integer, ItemStack> overflow = online.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            return;
        }
        online.saveData();
        try {
            deleteDeliveryRow(delivery.id);
            deliveries.remove(delivery.id, delivery);
            DeliveryMarker.clearFromInventory(plugin, online, delivery.id);
            online.saveData();
        } catch (SQLException e) {
            plugin.getLogger().severe("Buy order delivery acknowledgement failed: " + e.getMessage());
        }
    }

    public void claimPendingDeliveries(Player player) {
        reconcileMarkers(player);
        for (DeliveryImpl delivery : deliveries.values()) {
            if (delivery.buyer.equals(player.getUniqueId())
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
                try {
                    deleteDeliveryRow(id);
                    deliveries.remove(id, delivery);
                } catch (SQLException e) {
                    plugin.getLogger().severe("Buy order delivery reconciliation failed: " + e.getMessage());
                    continue;
                }
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
