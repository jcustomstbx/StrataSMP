package com.stratasmp.strataeconomy.auction;

import com.stratasmp.strataeconomy.StrataEconomy;
import com.stratasmp.strataeconomy.api.AuctionSaleEvent;
import com.stratasmp.strataeconomy.api.Auctions;
import com.stratasmp.strataeconomy.currency.Database;
import com.stratasmp.strataranks.RankTier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class AuctionStore implements Auctions {

    private final StrataEconomy plugin;
    private final Database db;
    private final ConcurrentHashMap<UUID, Impl> listings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Delivery> deliveries = new ConcurrentHashMap<>();

    private final int maxPerPlayer;
    private final long minPrice;
    private final long maxPrice;
    private final double listingFeePercent;

    public AuctionStore(StrataEconomy plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
        var cfg = plugin.getConfig();
        this.maxPerPlayer = cfg.getInt("auction.max-listings-per-player", 5);
        this.minPrice = cfg.getLong("auction.min-price", 1);
        this.maxPrice = cfg.getLong("auction.max-price", 100_000_000L);
        this.listingFeePercent = cfg.getDouble("auction.listing-fee-percent", 0.0);
        loadAll();
        loadDeliveries();
    }

    private void loadAll() {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, seller, seller_name, item_b64, price, listed_at, featured_until FROM stratas_auctions");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ItemStack item = fromB64(rs.getString("item_b64"));
                if (item == null) {
                    continue;
                }
                Impl l = new Impl(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("seller")),
                        rs.getString("seller_name"),
                        item, rs.getLong("price"), rs.getLong("listed_at"), rs.getLong("featured_until"));
                listings.put(l.id, l);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load auctions: " + e.getMessage());
        }
        plugin.getLogger().info("Auctions: loaded " + listings.size() + " listing(s).");
    }

    private void loadDeliveries() {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, buyer, item_b64, delivered_at FROM stratas_auction_deliveries");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ItemStack item = fromB64(rs.getString("item_b64"));
                if (item == null) continue;
                Delivery delivery = new Delivery(UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("buyer")), item, rs.getLong("delivered_at"));
                deliveries.put(delivery.id, delivery);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load auction deliveries: " + e.getMessage());
        }
    }

    @Override
    public List<Listing> all() {
        long now = System.currentTimeMillis();
        return listings.values().stream()
                .sorted((a, b) -> {
                    int featured = Boolean.compare(b.featuredUntil > now, a.featuredUntil > now);
                    return featured != 0 ? featured : Long.compare(b.listedAt, a.listedAt);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<Listing> bySeller(UUID seller) {
        return listings.values().stream()
                .filter(l -> l.seller.equals(seller))
                .collect(Collectors.toList());
    }

    @Override
    public Listing byId(UUID id) {
        return listings.get(id);
    }

    public int maxPerPlayer() {
        return maxPerPlayer;
    }

    public int maxPerPlayer(Player player) {
        return maxPerPlayer + switch (RankTier.of(player)) {
            case ORNATE -> 3;
            case REGAL -> 8;
            default -> 0;
        };
    }

    public long minPrice() {
        return minPrice;
    }

    public long maxPrice() {
        return maxPrice;
    }

    public long listingFee(long price) {
        return listingFeePercent <= 0 ? 0 : (long) Math.floor(price * listingFeePercent / 100.0);
    }

    @Override
    public Listing list(Player seller, ItemStack item, long price) {
        long now = System.currentTimeMillis();
        long featuredCount = listings.values().stream()
                .filter(existing -> existing.seller.equals(seller.getUniqueId()) && existing.featuredUntil > now)
                .count();
        long featuredUntil = RankTier.of(seller) == RankTier.REGAL && featuredCount < 5
                ? now + 24L * 60L * 60L * 1000L : 0L;
        Impl l = new Impl(UUID.randomUUID(), seller.getUniqueId(), seller.getName(),
                item.clone(), price, now, featuredUntil);
        final String b64 = toB64(l.item);
        try {
            db.transaction(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO stratas_auctions (id, seller, seller_name, item_b64, price, listed_at, featured_until) VALUES (?,?,?,?,?,?,?)")) {
                ps.setString(1, l.id.toString());
                ps.setString(2, l.seller.toString());
                ps.setString(3, l.sellerName);
                ps.setString(4, b64);
                ps.setLong(5, l.price);
                ps.setLong(6, l.listedAt);
                ps.setLong(7, l.featuredUntil);
                ps.executeUpdate();
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException("Could not persist auction listing", e);
        }
        listings.put(l.id, l);
        return l;
    }

    @Override
    public String buy(Player buyer, UUID listingId) {
        Impl l = listings.get(listingId);
        if (l == null) {
            return plugin.msgRaw("ah-gone");
        }
        if (l.seller.equals(buyer.getUniqueId())) {
            return plugin.msgRaw("ah-own-listing");
        }
        if (!plugin.stratas().has(buyer.getUniqueId(), l.price)) {
            return plugin.msgRaw("shop-cant-afford");
        }

        AuctionSaleEvent event = new AuctionSaleEvent(l.seller, buyer, l.item, l.price);
        Bukkit.getPluginManager().callEvent(event);
        long tax = Math.max(0L, Math.min(l.price, event.getTax()));
        long payout = l.price - tax;

        java.util.Map<UUID, Long> deltas = new java.util.HashMap<>();
        deltas.put(buyer.getUniqueId(), -l.price);
        if (payout > 0) {
            deltas.merge(l.seller, payout, Long::sum);
        }
        Delivery delivery = new Delivery(UUID.randomUUID(), buyer.getUniqueId(), l.item.clone(),
                System.currentTimeMillis());
        try {
            boolean committed = plugin.stratas().applyAtomicDeltas(deltas, c -> {
                insertDelivery(c, delivery);
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM stratas_auctions WHERE id=?")) {
                    ps.setString(1, listingId.toString());
                    if (ps.executeUpdate() != 1) {
                        throw new java.sql.SQLException("Auction listing was already removed");
                    }
                }
            });
            if (!committed) {
                return plugin.msgRaw("shop-cant-afford");
            }
        } catch (java.sql.SQLException e) {
            plugin.getLogger().severe("Auction purchase transaction failed: " + e.getMessage());
            return "The purchase could not be saved. No Stratas were charged; please try again.";
        }
        listings.remove(listingId, l);
        deliveries.put(delivery.id, delivery);
        boolean delivered = deliverOne(buyer, delivery);

        Player sellerOnline = Bukkit.getPlayer(l.seller);
        if (sellerOnline != null) {
            plugin.msg().send(sellerOnline, "ah-sold", java.util.Map.of(
                    "player", buyer.getName(),
                    "item", l.item.getType().name().toLowerCase().replace('_', ' '),
                    "price", plugin.money(payout)));
        }
        if (!delivered) {
            buyer.sendMessage("Your purchase is safe in your item inbox. Free an inventory slot, then use /ah claimitems.");
        }
        return null;
    }

    @Override
    public String cancel(Player who, UUID listingId) {
        Impl l = listings.get(listingId);
        if (l == null) {
            return plugin.msgRaw("ah-gone");
        }
        if (!l.seller.equals(who.getUniqueId()) && !who.hasPermission("strataeconomy.admin")) {
            return plugin.msgRaw("ah-not-yours");
        }
        Delivery delivery = new Delivery(UUID.randomUUID(), who.getUniqueId(), l.item.clone(),
                System.currentTimeMillis());
        try {
            db.transaction(c -> {
                insertDelivery(c, delivery);
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM stratas_auctions WHERE id=?")) {
                    ps.setString(1, listingId.toString());
                    if (ps.executeUpdate() != 1) {
                        throw new java.sql.SQLException("Auction listing was already removed");
                    }
                }
            });
        } catch (java.sql.SQLException e) {
            plugin.getLogger().severe("Auction cancellation transaction failed: " + e.getMessage());
            return "The cancellation could not be saved. Your item was not returned; please try again.";
        }
        listings.remove(listingId, l);
        deliveries.put(delivery.id, delivery);
        if (!deliverOne(who, delivery)) {
            who.sendMessage("Your returned item is safe in your item inbox. Free a slot, then use /ah claimitems.");
        }
        return null;
    }

    @Override
    public boolean adminRemove(UUID listingId) {
        Impl l = listings.get(listingId);
        if (l == null) {
            return false;
        }
        try {
            db.transaction(c -> {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM stratas_auctions WHERE id=?")) {
                    ps.setString(1, listingId.toString());
                    ps.executeUpdate();
                }
            });
        } catch (java.sql.SQLException e) {
            plugin.getLogger().severe("Admin removal of auction listing failed: " + e.getMessage());
            return false;
        }
        listings.remove(listingId, l);
        return true;
    }

    public int claimDeliveries(Player player) {
        reconcileMarkers(player);
        int claimed = 0;
        for (Delivery delivery : deliveries.values()) {
            if (!delivery.buyer.equals(player.getUniqueId())) continue;
            if (DeliveryMarker.present(player, plugin, delivery.id)) continue;
            if (deliverOne(player, delivery)) claimed++;
        }
        return claimed;
    }

    private void reconcileMarkers(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        boolean changed = false;
        for (ItemStack item : contents) {
            UUID id = DeliveryMarker.id(plugin, item);
            if (id == null) continue;
            if (deliveries.containsKey(id)) {
                try {
                    deleteDelivery(id);
                    deliveries.remove(id);
                } catch (java.sql.SQLException e) {
                    plugin.getLogger().severe("Auction delivery reconciliation failed: " + e.getMessage());
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

    private boolean deliverOne(Player player, Delivery delivery) {
        ItemStack marked = DeliveryMarker.mark(plugin, delivery.item, delivery.id);
        if (!canFullyFit(player, marked)) return false;
        java.util.Map<Integer, ItemStack> overflow = player.getInventory().addItem(marked);
        if (!overflow.isEmpty()) return false;
        player.saveData();
        try {
            deleteDelivery(delivery.id);
            deliveries.remove(delivery.id, delivery);
            DeliveryMarker.clearFromInventory(plugin, player, delivery.id);
            player.saveData();
            return true;
        } catch (java.sql.SQLException e) {
            plugin.getLogger().severe("Auction delivery acknowledgement failed: " + e.getMessage());
            return false;
        }
    }

    private boolean canFullyFit(Player player, ItemStack item) {
        int remaining = item.getAmount();
        for (ItemStack existing : player.getInventory().getStorageContents()) {
            if (existing == null || existing.getType().isAir()) {
                remaining -= item.getMaxStackSize();
            } else if (existing.isSimilar(item)) {
                remaining -= Math.max(0, item.getMaxStackSize() - existing.getAmount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private void insertDelivery(Connection c, Delivery delivery) throws java.sql.SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO stratas_auction_deliveries (id, buyer, item_b64, delivered_at) VALUES (?,?,?,?)")) {
            ps.setString(1, delivery.id.toString());
            ps.setString(2, delivery.buyer.toString());
            ps.setString(3, toB64(delivery.item));
            ps.setLong(4, delivery.deliveredAt);
            ps.executeUpdate();
        }
    }

    private void deleteDelivery(UUID id) throws java.sql.SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM stratas_auction_deliveries WHERE id=?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        }
    }

    private record Delivery(UUID id, UUID buyer, ItemStack item, long deliveredAt) { }

    /* ---- item base64 ---- */

    static String toB64(ItemStack item) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bos)) {
            out.writeObject(item);
            return Base64Coder.encodeLines(bos.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("serialise item", e);
        }
    }

    static ItemStack fromB64(String data) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64Coder.decodeLines(data));
             BukkitObjectInputStream in = new BukkitObjectInputStream(bis)) {
            return (ItemStack) in.readObject();
        } catch (Exception e) {
            return null;
        }
    }

    private record Impl(UUID id, UUID seller, String sellerName, ItemStack item, long price, long listedAt, long featuredUntil)
            implements Listing {
    }
}
