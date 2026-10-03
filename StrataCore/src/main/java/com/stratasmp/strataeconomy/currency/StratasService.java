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
    /** Unflushed absolute balances, stamped so a late flush can never overwrite a newer write. */
    private record Pending(long balance, long stamp) {}
    private final Map<UUID, Pending> dirty = new ConcurrentHashMap<>();
    /** Deposits made while the database could not be read; applied additively once it can. */
    private final Map<UUID, Long> pendingDeltas = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong lastStamp = new java.util.concurrent.atomic.AtomicLong();

    private final java.util.concurrent.atomic.AtomicBoolean flushQueued = new java.util.concurrent.atomic.AtomicBoolean();

    private long nextStamp() {
        return lastStamp.updateAndGet(prev -> Math.max(prev + 1, System.currentTimeMillis()));
    }

    public StratasService(StrataEconomy plugin, Database db, long startingBalance) {
        this.plugin = plugin;
        this.db = db;
        this.startingBalance = startingBalance;
    }

    /* ---- cache warmup ---- */

    /** Call off the main thread (AsyncPlayerPreLogin). Loads/creates the row. */
    public long preload(UUID uuid, String username) {
        Long loaded = tryLoad(uuid, username);
        // on a failed read the player sees the starting balance but nothing is cached or written,
        // so a database hiccup can never overwrite a real balance
        return loaded != null ? loaded : startingBalance;
    }

    /** @return the balance now in the cache, or null if the database could not be read. */
    private Long tryLoad(UUID uuid, String username) {
        Long cached = cache.get(uuid);
        if (cached != null) {
            if (username != null) {
                names.put(uuid, username);
                touchName(uuid, username);
            }
            return cached;
        }
        long bal;
        boolean existed = false;
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("SELECT balance FROM stratas_balances WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    bal = rs.getLong(1);
                    existed = true;
                } else {
                    bal = startingBalance;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("preload(" + uuid + ") failed, balance left uncached: " + e.getMessage());
            return null;
        }
        boolean fold = false;
        Long delta = pendingDeltas.remove(uuid);
        if (delta != null) {
            bal = saturatingAdd(bal, delta);
            fold = true;
        }
        synchronized (this) {
            Long raced = cache.putIfAbsent(uuid, bal);
            if (raced != null) {
                if (delta != null) pendingDeltas.merge(uuid, delta, StratasService::saturatingAdd);
                return raced;
            }
        }
        if (username != null) {
            names.put(uuid, username);
        }
        if (!existed || fold) {
            dirty.put(uuid, new Pending(bal, nextStamp()));
            flushAsync();
        } else if (username != null) {
            touchName(uuid, username);
        }
        return bal;
    }

    private static long saturatingAdd(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException overflow) {
            return b > 0 ? Long.MAX_VALUE : 0L;
        }
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

    /** In-memory balance only; never touches the database. */
    public Long cachedBalance(UUID uuid) {
        return cache.get(uuid);
    }

    /** True when a balance for this player is held in memory (or can be read now). */
    private Long known(UUID uuid) {
        Long v = cache.get(uuid);
        return v != null ? v : tryLoad(uuid, null);
    }

    public boolean has(UUID uuid, long amount) {
        return getBalance(uuid) >= amount;
    }

    /* ---- writes ---- */

    public synchronized void set(UUID uuid, long amount) {
        long v = Math.max(0L, amount);
        cache.put(uuid, v);
        dirty.put(uuid, new Pending(v, nextStamp()));
        scheduleFlush();
    }

    /** Writes soon after a change (batching what lands before the worker starts) instead of waiting for the timer. */
    private void scheduleFlush() {
        if (flushQueued.compareAndSet(false, true)) {
            plugin.async(() -> {
                flushQueued.set(false);
                flushNow();
            });
        }
    }

    public synchronized void deposit(UUID uuid, long amount) {
        if (amount <= 0) {
            return;
        }
        Long cur = known(uuid);
        if (cur == null) {
            pendingDeltas.merge(uuid, amount, StratasService::saturatingAdd);
            return;
        }
        set(uuid, saturatingAdd(cur, amount));
    }

    public synchronized boolean withdraw(UUID uuid, long amount) {
        if (amount <= 0) {
            return true;
        }
        Long cur = known(uuid);
        if (cur == null || cur < amount) {
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
            Long known = known(entry.getKey());
            if (known == null) {
                throw new SQLException("Stratas balance unavailable for " + entry.getKey());
            }
            long current = known;
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
                long now = nextStamp();
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

    /**
     * Writes everything queued. The SQL runs outside the service monitor so shop and pay calls on the main
     * thread never wait on the database; each row carries its mutation stamp so a late write is ignored.
     */
    public void flushNow() {
        if (dirty.isEmpty() && pendingDeltas.isEmpty()) {
            return;
        }
        Map<UUID, Pending> batch = new java.util.HashMap<>(dirty);
        String sql = "INSERT INTO stratas_balances (uuid, username, balance, updated_at) VALUES (?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE username=IF(VALUES(username)='', username, VALUES(username)), "
                + "balance=IF(VALUES(updated_at) >= updated_at, VALUES(balance), balance), "
                + "updated_at=GREATEST(updated_at, VALUES(updated_at))";
        if (!batch.isEmpty()) {
            try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
                for (Map.Entry<UUID, Pending> e : batch.entrySet()) {
                    ps.setString(1, e.getKey().toString());
                    ps.setString(2, names.getOrDefault(e.getKey(), ""));
                    ps.setLong(3, e.getValue().balance());
                    ps.setLong(4, e.getValue().stamp());
                    ps.addBatch();
                }
                ps.executeBatch();
                for (Map.Entry<UUID, Pending> e : batch.entrySet()) {
                    dirty.remove(e.getKey(), e.getValue());
                }
            } catch (Exception ex) {
                plugin.getLogger().severe("flush failed, " + batch.size() + " balances stay queued: " + ex.getMessage());
            }
        }
        flushPendingDeltas();
    }

    private void flushPendingDeltas() {
        for (Map.Entry<UUID, Long> e : new java.util.HashMap<>(pendingDeltas).entrySet()) {
            try (Connection c = db.connection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO stratas_balances (uuid, username, balance, updated_at) VALUES (?,?,?,?) "
                                 + "ON DUPLICATE KEY UPDATE balance=balance+?, updated_at=VALUES(updated_at)")) {
                ps.setString(1, e.getKey().toString());
                ps.setString(2, names.getOrDefault(e.getKey(), ""));
                ps.setLong(3, saturatingAdd(startingBalance, e.getValue()));
                ps.setLong(4, nextStamp());
                ps.setLong(5, e.getValue());
                ps.executeUpdate();
                if (pendingDeltas.remove(e.getKey(), e.getValue())) {
                    cache.computeIfPresent(e.getKey(), (k, v) -> saturatingAdd(v, e.getValue()));
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("pending deposit for " + e.getKey() + " still queued: " + ex.getMessage());
            }
        }
    }

    public void evict(UUID uuid) {
        cache.remove(uuid);
        names.remove(uuid);
    }
}
