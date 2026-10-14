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
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class AuctionStore implements Auctions {

    private final StrataEconomy plugin;
    private final Database db;
    private final ConcurrentHashMap<UUID, Impl> listings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Delivery> deliveries = new ConcurrentHashMap<>();
    // players with a save in flight, listings and deliveries a database write is working on
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<UUID> handedOut = ConcurrentHashMap.newKeySet();
    private final Set<UUID> deleting = ConcurrentHashMap.newKeySet();

    private static final String WAIT = "Please wait a moment, your last auction action is still being saved.";

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
    public void list(Player seller, ItemStack item, long price, Consumer<Listing> done) {
        UUID sellerId = seller.getUniqueId();
        if (!busy.add(sellerId)) {
            done.accept(null);
            return;
        }
        long now = System.currentTimeMillis();
        long featuredCount = listings.values().stream()
                .filter(existing -> existing.seller.equals(sellerId) && existing.featuredUntil > now)
                .count();
        long featuredUntil = RankTier.of(seller) == RankTier.REGAL && featuredCount < 5
                ? now + 24L * 60L * 60L * 1000L : 0L;
        Impl l = new Impl(UUID.randomUUID(), sellerId, seller.getName(), item.clone(), price, now, featuredUntil);
        final String b64 = toB64(l.item);
        plugin.offload(() -> {
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
            return l;
        }, (saved, failure) -> {
            busy.remove(sellerId);
            if (failure != null) {
                plugin.getLogger().severe("Could not persist auction listing: " + failure.getMessage());
                done.accept(null);
                return;
            }
            listings.put(l.id, l);
            done.accept(l);
        });
    }

    @Override
    public void buy(Player buyer, UUID listingId, Consumer<String> done) {
        Impl l = listings.get(listingId);
        if (l == null || inFlight.contains(listingId)) {
            done.accept(plugin.msgRaw("ah-gone"));
            return;
        }
        UUID buyerId = buyer.getUniqueId();
        if (l.seller.equals(buyerId)) {
            done.accept(plugin.msgRaw("ah-own-listing"));
            return;
        }
        if (!plugin.stratas().has(buyerId, l.price)) {
            done.accept(plugin.msgRaw("shop-cant-afford"));
            return;
        }
        if (!busy.add(buyerId)) {
            done.accept(WAIT);
            return;
        }
        if (!inFlight.add(listingId)) {
            busy.remove(buyerId);
            done.accept(plugin.msgRaw("ah-gone"));
            return;
        }

        AuctionSaleEvent event = new AuctionSaleEvent(l.seller, buyer, l.item, l.price);
        Bukkit.getPluginManager().callEvent(event);
        long tax = Math.max(0L, Math.min(l.price, event.getTax()));
        long payout = l.price - tax;

        java.util.Map<UUID, Long> deltas = new java.util.HashMap<>();
        deltas.put(buyerId, -l.price);
        if (payout > 0) {
            deltas.merge(l.seller, payout, Long::sum);
        }
        Delivery delivery = new Delivery(UUID.randomUUID(), buyerId, l.item.clone(), System.currentTimeMillis());
        plugin.offload(() -> plugin.stratas().applyAtomicDeltas(deltas, c -> {
            insertDelivery(c, delivery);
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM stratas_auctions WHERE id=?")) {
                ps.setString(1, listingId.toString());
                if (ps.executeUpdate() != 1) {
                    throw new java.sql.SQLException("Auction listing was already removed");
                }
            }
        }), (committed, failure) -> {
            inFlight.remove(listingId);
            busy.remove(buyerId);
            if (failure != null) {
                plugin.getLogger().severe("Auction purchase transaction failed: " + failure.getMessage());
                done.accept("The purchase could not be saved. No Stratas were charged; please try again.");
                return;
            }
            if (!committed) {
                done.accept(plugin.msgRaw("shop-cant-afford"));
                return;
            }
            listings.remove(listingId, l);
            deliveries.put(delivery.id, delivery);
            boolean delivered = buyer.isOnline() && deliverOne(buyer, delivery);

            Player sellerOnline = Bukkit.getPlayer(l.seller);
            if (sellerOnline != null) {
                plugin.msg().send(sellerOnline, "ah-sold", java.util.Map.of(
                        "player", buyer.getName(),
                        "item", l.item.getType().name().toLowerCase().replace('_', ' '),
                        "price", plugin.money(payout)));
            }
            if (!delivered && buyer.isOnline()) {
                buyer.sendMessage("Your purchase is safe in your item inbox. Free an inventory slot, then use /ah claimitems.");
            }
            done.accept(null);
        });
    }

    @Override
    public void cancel(Player who, UUID listingId, Consumer<String> done) {
        Impl l = listings.get(listingId);
        if (l == null || inFlight.contains(listingId)) {
            done.accept(plugin.msgRaw("ah-gone"));
            return;
        }
        if (!l.seller.equals(who.getUniqueId()) && !who.hasPermission("strataeconomy.admin")) {
            done.accept(plugin.msgRaw("ah-not-yours"));
            return;
        }
        UUID whoId = who.getUniqueId();
        if (!busy.add(whoId)) {
            done.accept(WAIT);
            return;
        }
        if (!inFlight.add(listingId)) {
            busy.remove(whoId);
            done.accept(plugin.msgRaw("ah-gone"));
            return;
        }
        Delivery delivery = new Delivery(UUID.randomUUID(), whoId, l.item.clone(), System.currentTimeMillis());
        plugin.offload(() -> {
            db.transaction(c -> {
                insertDelivery(c, delivery);
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM stratas_auctions WHERE id=?")) {
                    ps.setString(1, listingId.toString());
                    if (ps.executeUpdate() != 1) {
                        throw new java.sql.SQLException("Auction listing was already removed");
                    }
                }
            });
            return true;
        }, (ok, failure) -> {
            inFlight.remove(listingId);
            busy.remove(whoId);
            if (failure != null) {
                plugin.getLogger().severe("Auction cancellation transaction failed: " + failure.getMessage());
                done.accept("The cancellation could not be saved. Your item was not returned; please try again.");
                return;
            }
            listings.remove(listingId, l);
            deliveries.put(delivery.id, delivery);
            if (who.isOnline() && !deliverOne(who, delivery)) {
                who.sendMessage("Your returned item is safe in your item inbox. Free a slot, then use /ah claimitems.");
            }
            done.accept(null);
        });
    }

    @Override
    public boolean adminRemove(UUID listingId) {
        Impl l = listings.get(listingId);
        if (l == null || inFlight.contains(listingId)) {
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
            if (handedOut.contains(delivery.id) || DeliveryMarker.present(player, plugin, delivery.id)) continue;
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
            Delivery delivery = deliveries.get(id);
            if (delivery != null) {
                // the marker stays on the item until the row is really gone
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

    /** Puts the item in the inventory under a marker; the row is deleted in the background and the marker then cleared. */
    private boolean deliverOne(Player player, Delivery delivery) {
        ItemStack marked = DeliveryMarker.mark(plugin, delivery.item, delivery.id);
        if (!canFullyFit(player, marked)) return false;
        java.util.Map<Integer, ItemStack> overflow = player.getInventory().addItem(marked);
        if (!overflow.isEmpty()) return false;
        handedOut.add(delivery.id);
        player.saveData();
        scheduleDelete(player.getUniqueId(), delivery);
        return true;
    }

    private void scheduleDelete(UUID playerId, Delivery delivery) {
        if (!deleting.add(delivery.id)) return;
        plugin.offload(() -> {
            deleteDelivery(delivery.id);
            return true;
        }, (ok, failure) -> {
            deleting.remove(delivery.id);
            if (failure != null) {
                // stays handed out, so it can't be given again this session; the next join retries the delete
                plugin.getLogger().severe("Auction delivery acknowledgement failed: " + failure.getMessage());
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
