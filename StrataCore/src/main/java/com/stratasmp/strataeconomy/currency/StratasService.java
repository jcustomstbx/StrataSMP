package com.stratasmp.strataeconomy.currency;

import com.stratasmp.strataeconomy.StrataEconomy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative balance store. Reads come from an in-memory cache so Vault's
 * synchronous getBalance() stays fast on the main thread; every mutation
 * updates the cache immediately and is flushed to MySQL on an async worker.
 */
public final class StratasService {

    private final StrataEconomy plugin;
    private final Database db;
    private final long startingBalance;

    private final Map<UUID, Long> cache = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dirty = new ConcurrentHashMap<>();

    public StratasService(StrataEconomy plugin, Database db, long startingBalance) {
        this.plugin = plugin;
        this.db = db;
        this.startingBalance = startingBalance;
    }

    /* ---- cache warmup ---- */

    /** Call off the main thread (AsyncPlayerPreLogin). Loads/creates the row. */
    public long preload(UUID uuid, String username) {
        Long cached = cache.get(uuid);
        if (cached != null) {
            if (username != null) {
                names.put(uuid, username);
                touchName(uuid, username);
            }
            return cached;
        }
        long bal = startingBalance;
        boolean existed = false;
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("SELECT balance FROM stratas_balances WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    bal = rs.getLong(1);
                    existed = true;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("preload(" + uuid + ") failed: " + e.getMessage());
        }
        cache.put(uuid, bal);
        if (username != null) {
            names.put(uuid, username);
        }
        if (!existed) {
            dirty.put(uuid, bal);
            flushAsync();
        } else if (username != null) {
            touchName(uuid, username);
        }
        return bal;
    }

    private void touchName(UUID uuid, String username) {
        plugin.async(() -> {
            try (Connection c = db.connection();
                 PreparedStatement ps = c.prepareStatement(
                         "UPDATE stratas_balances SET username=? WHERE uuid=?")) {
                ps.setString(1, username);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            } catch (Exception ignored) {
            }
        });
    }

    /* ---- reads ---- */

    public long getBalance(UUID uuid) {
        Long v = cache.get(uuid);
        if (v != null) {
            return v;
        }
        return preload(uuid, null);
    }

    public boolean has(UUID uuid, long amount) {
        return getBalance(uuid) >= amount;
    }

    /* ---- writes ---- */

    public synchronized void set(UUID uuid, long amount) {
        long v = Math.max(0L, amount);
        cache.put(uuid, v);
        dirty.put(uuid, v);
    }

    public synchronized void deposit(UUID uuid, long amount) {
        if (amount <= 0) {
            return;
        }
        set(uuid, getBalance(uuid) + amount);
    }

    public synchronized boolean withdraw(UUID uuid, long amount) {
        if (amount <= 0) {
            return true;
        }
        long cur = getBalance(uuid);
        if (cur < amount) {
            return false;
        }
        set(uuid, cur - amount);
        return true;
    }

    /**
     * Atomically persists balance deltas together with a related marketplace row change.
     * The callback and balance writes share one InnoDB transaction. Cache state changes only
     * after commit, so a database failure leaves the in-memory economy untouched.
     *
     * @return false when any resulting balance would be negative
     */
    public synchronized boolean applyAtomicDeltas(Map<UUID, Long> deltas,
                                                  Database.TransactionWork relatedRows) throws SQLException {
        Map<UUID, Long> updated = new ConcurrentHashMap<>();
        for (Map.Entry<UUID, Long> entry : deltas.entrySet()) {
            long current = getBalance(entry.getKey());
            long next;
            try {
                next = Math.addExact(current, entry.getValue());
            } catch (ArithmeticException overflow) {
                throw new SQLException("Stratas balance overflow for " + entry.getKey(), overflow);
            }
            if (next < 0) {
                return false;
            }
            updated.put(entry.getKey(), next);
        }

        db.transaction(connection -> {
            String sql = "INSERT INTO stratas_balances (uuid, username, balance, updated_at) VALUES (?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE balance=VALUES(balance), updated_at=VALUES(updated_at)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                long now = System.currentTimeMillis();
                for (Map.Entry<UUID, Long> entry : updated.entrySet()) {
                    ps.setString(1, entry.getKey().toString());
                    ps.setString(2, names.getOrDefault(entry.getKey(), ""));
                    ps.setLong(3, entry.getValue());
                    ps.setLong(4, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            relatedRows.run(connection);
        });

        for (Map.Entry<UUID, Long> entry : updated.entrySet()) {
            cache.put(entry.getKey(), entry.getValue());
            dirty.remove(entry.getKey());
        }
        return true;
    }

    /* ---- top ---- */

    public List<Map.Entry<UUID, Long>> top(int limit) {
        List<Map.Entry<UUID, Long>> out = new ArrayList<>();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, balance FROM stratas_balances ORDER BY balance DESC LIMIT ?")) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new AbstractMap.SimpleEntry<>(UUID.fromString(rs.getString(1)), rs.getLong(2)));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("top() failed: " + e.getMessage());
        }
        return out;
    }

    public String nameOf(UUID uuid) {
        String n = names.get(uuid);
        if (n != null) {
            return n;
        }
        var off = plugin.getServer().getOfflinePlayer(uuid);
        return off.getName() != null ? off.getName() : uuid.toString().substring(0, 8);
    }

    /* ---- persistence ---- */

    public void flushAsync() {
        if (dirty.isEmpty()) {
            return;
        }
        plugin.async(this::flushNow);
    }

    public synchronized void flushNow() {
        if (dirty.isEmpty()) {
            return;
        }
        Map<UUID, Long> batch = new ConcurrentHashMap<>(dirty);
        dirty.clear();
        long now = System.currentTimeMillis();
        String sql = "INSERT INTO stratas_balances (uuid, username, balance, updated_at) VALUES (?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE username=VALUES(username), balance=VALUES(balance), updated_at=VALUES(updated_at)";
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (Map.Entry<UUID, Long> e : batch.entrySet()) {
                ps.setString(1, e.getKey().toString());
                ps.setString(2, names.getOrDefault(e.getKey(), ""));
                ps.setLong(3, e.getValue());
                ps.setLong(4, now);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Exception ex) {
            dirty.putAll(batch);
            plugin.getLogger().severe("flush failed, re-queued " + batch.size() + " balances: " + ex.getMessage());
        }
    }

    public void evict(UUID uuid) {
        cache.remove(uuid);
        names.remove(uuid);
    }
}
