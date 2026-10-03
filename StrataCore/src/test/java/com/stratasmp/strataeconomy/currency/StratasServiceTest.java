package com.stratasmp.strataeconomy.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.stratasmp.strataeconomy.StrataEconomy;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the real StratasService against an in-memory H2 database in MySQL mode. */
class StratasServiceTest {

    /** A data source that can be switched off to simulate the database being unreachable. */
    private static final class Flaky implements DataSource {
        private final DataSource real;
        volatile boolean down;
        final java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();

        Flaky(DataSource real) {
            this.real = real;
        }

        @Override public Connection getConnection() throws SQLException {
            attempts.incrementAndGet();
            if (down) throw new SQLException("database is down");
            return real.getConnection();
        }

        @Override public Connection getConnection(String u, String p) throws SQLException { return getConnection(); }
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { throw new SQLFeatureNotSupportedException(); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("not a wrapper"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    @TempDir Path dataDir;
    private Flaky source;
    private Database database;
    private StrataEconomy plugin;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        source = new Flaky(h2);
        try (Connection c = source.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE stratas_balances (uuid CHAR(36) NOT NULL PRIMARY KEY, "
                    + "username VARCHAR(16) NOT NULL DEFAULT '', balance BIGINT NOT NULL DEFAULT 0, "
                    + "updated_at BIGINT NOT NULL DEFAULT 0)");
        }
        database = new Database(source);
        plugin = mock(StrataEconomy.class);
        File folder = dataDir.toFile();
        when(plugin.getDataFolder()).thenReturn(folder);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        // "async" work runs inline so the tests are deterministic
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; }).when(plugin).async(any(Runnable.class));
    }

    private StratasService service(long starting) {
        return new StratasService(plugin, database, starting);
    }

    private void insertRow(UUID id, long balance, long updatedAt) throws SQLException {
        try (Connection c = source.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO stratas_balances (uuid, balance, updated_at) VALUES (?,?,?)")) {
            ps.setString(1, id.toString());
            ps.setLong(2, balance);
            ps.setLong(3, updatedAt);
            ps.executeUpdate();
        }
    }

    private Long dbBalance(UUID id) throws SQLException {
        try (Connection c = source.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT balance FROM stratas_balances WHERE uuid=?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    @Test
    void newPlayerStartsWithTheStartingBalanceAndIsStored() throws Exception {
        StratasService svc = service(250);
        UUID id = UUID.randomUUID();

        assertEquals(250, svc.preload(id, "Steve"));
        svc.flushNow();

        assertEquals(250L, dbBalance(id));
    }

    @Test
    void depositAndWithdrawChangeTheBalanceAndPersist() throws Exception {
        StratasService svc = service(100);
        UUID id = UUID.randomUUID();
        svc.preload(id, "Alex");

        svc.deposit(id, 50);
        assertTrue(svc.withdraw(id, 70));
        svc.flushNow();

        assertEquals(80, svc.getBalance(id));
        assertEquals(80L, dbBalance(id));
    }

    @Test
    void withdrawRefusesWhenFundsAreShortAndChangesNothing() throws Exception {
        StratasService svc = service(100);
        UUID id = UUID.randomUUID();
        svc.preload(id, "Alex");

        assertFalse(svc.withdraw(id, 101));
        assertEquals(100, svc.getBalance(id));
    }

    @Test
    void nonPositiveAmountsAreIgnored() {
        StratasService svc = service(10);
        UUID id = UUID.randomUUID();
        svc.preload(id, "Alex");

        svc.deposit(id, 0);
        svc.deposit(id, -5);

        assertEquals(10, svc.getBalance(id));
        assertTrue(svc.withdraw(id, 0));
    }

    @Test
    void depositsSaturateInsteadOfWrapping() {
        StratasService svc = service(0);
        UUID id = UUID.randomUUID();
        svc.preload(id, "Rich");
        svc.set(id, Long.MAX_VALUE - 1);

        svc.deposit(id, 1_000_000);

        assertEquals(Long.MAX_VALUE, svc.getBalance(id));
    }

    @Test
    void failedReadNeverOverwritesARealBalance() throws Exception {
        UUID id = UUID.randomUUID();
        insertRow(id, 5000, 1);
        StratasService svc = service(0);

        source.down = true;
        svc.preload(id, "Steve");          // read fails: nothing may be cached or written
        assertFalse(svc.withdraw(id, 1), "cannot spend a balance that could not be read");
        svc.deposit(id, 10);               // held back, not applied to a guess
        source.down = false;

        assertEquals(5000L, dbBalance(id));
    }

    @Test
    void depositsHeldDuringAnOutageAreAppliedExactlyOnce() throws Exception {
        UUID id = UUID.randomUUID();
        insertRow(id, 5000, 1);
        StratasService svc = service(0);

        source.down = true;
        svc.deposit(id, 10);
        svc.deposit(id, 15);
        source.down = false;

        svc.flushNow();                    // the retry loads the balance and folds the held deposits in
        svc.flushNow();
        svc.flushNow();

        assertEquals(5025, svc.getBalance(id));
        assertEquals(5025L, dbBalance(id));
        assertFalse(new File(dataDir.toFile(), "pending-deposits.yml").exists(), "journal is cleared once applied");
    }

    @Test
    void afterAFailedReadTheDatabaseIsNotHammeredByEveryCall() {
        StratasService svc = service(0);
        UUID id = UUID.randomUUID();
        source.down = true;

        svc.getBalance(id);
        int afterFirst = source.attempts.get();
        for (int i = 0; i < 20; i++) {
            svc.getBalance(id);
            svc.has(id, 1);
        }

        assertEquals(afterFirst, source.attempts.get(), "callers must not each wait on a database that was just found down");
    }

    @Test
    void anAbsoluteSetReplacesDepositsHeldDuringAnOutage() throws Exception {
        UUID id = UUID.randomUUID();
        insertRow(id, 5000, 1);
        StratasService svc = service(0);
        source.down = true;
        svc.deposit(id, 25);
        assertTrue(new File(dataDir.toFile(), "pending-deposits.yml").exists());

        svc.set(id, 7);

        assertFalse(new File(dataDir.toFile(), "pending-deposits.yml").exists(), "the held deposit is dropped by the set");
        source.down = false;
        svc.flushNow();
        assertEquals(7L, dbBalance(id));
        assertEquals(7, service(0).getBalance(id), "a restart must not add the old deposit on top");
    }

    @Test
    void heldDepositsSurviveARestart() throws Exception {
        UUID id = UUID.randomUUID();
        insertRow(id, 100, 1);
        StratasService first = service(0);
        source.down = true;
        first.deposit(id, 25);
        assertTrue(new File(dataDir.toFile(), "pending-deposits.yml").exists());

        // a new service instance (server restart) once the database is back
        source.down = false;
        StratasService second = service(0);

        assertEquals(125, second.getBalance(id));
        second.flushNow();
        assertEquals(125L, dbBalance(id));
    }

    @Test
    void aStaleWriteCannotOverwriteANewerRow() throws Exception {
        UUID id = UUID.randomUUID();
        long farFuture = System.currentTimeMillis() + 10_000_000L;
        insertRow(id, 100, farFuture);
        StratasService svc = service(0);
        svc.preload(id, "Steve");

        svc.set(id, 5);                    // stamped "now", older than the row
        svc.flushNow();

        assertEquals(100L, dbBalance(id), "the newer row wins");
    }

    @Test
    void seedingTheStampClockFromTheDatabaseFixesAClockThatWentBackwards() throws Exception {
        UUID id = UUID.randomUUID();
        long farFuture = System.currentTimeMillis() + 10_000_000L;
        insertRow(id, 100, farFuture);
        StratasService svc = service(0);
        svc.seedStamp();
        svc.preload(id, "Steve");

        svc.set(id, 5);
        svc.flushNow();

        assertEquals(5L, dbBalance(id));
    }

    @Test
    void atomicDeltasMoveMoneyBetweenTwoPlayers() throws Exception {
        StratasService svc = service(0);
        UUID payer = UUID.randomUUID();
        UUID payee = UUID.randomUUID();
        svc.preload(payer, "Payer");
        svc.preload(payee, "Payee");
        svc.set(payer, 100);

        boolean ok = svc.applyAtomicDeltas(Map.of(payer, -30L, payee, 30L), c -> { });

        assertTrue(ok);
        assertEquals(70L, dbBalance(payer));
        assertEquals(30L, dbBalance(payee));
        assertEquals(70, svc.getBalance(payer));
        assertEquals(30, svc.getBalance(payee));
    }

    @Test
    void atomicDeltasRefuseToGoNegative() throws Exception {
        StratasService svc = service(0);
        UUID payer = UUID.randomUUID();
        svc.preload(payer, "Payer");
        svc.set(payer, 10);

        assertFalse(svc.applyAtomicDeltas(Map.of(payer, -11L), c -> { }));

        assertEquals(10, svc.getBalance(payer));
    }

    @Test
    void aFailingRelatedChangeRollsTheWholeTransactionBack() throws Exception {
        StratasService svc = service(0);
        UUID payer = UUID.randomUUID();
        svc.preload(payer, "Payer");
        svc.set(payer, 100);
        svc.flushNow();

        assertThrows(SQLException.class, () -> svc.applyAtomicDeltas(Map.of(payer, -40L), c -> {
            throw new SQLException("auction row was already removed");
        }));

        assertEquals(100, svc.getBalance(payer), "the cache is untouched");
        assertEquals(100L, dbBalance(payer), "the database is untouched");
    }

    @Test
    void concurrentDepositsAreNotLost() throws Exception {
        StratasService svc = service(0);
        UUID id = UUID.randomUUID();
        svc.preload(id, "Busy");

        int threads = 8;
        int each = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        for (int t = 0; t < threads; t++) {
            pool.execute(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int i = 0; i < each; i++) svc.deposit(id, 1);
            });
        }
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        svc.flushNow();

        assertEquals((long) threads * each, svc.getBalance(id));
        assertEquals((long) threads * each, dbBalance(id));
    }

    @Test
    void topListsTheRichestFirst() {
        StratasService svc = service(0);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        svc.preload(a, "A");
        svc.preload(b, "B");
        svc.set(a, 10);
        svc.set(b, 99);
        svc.flushNow();

        List<Map.Entry<UUID, Long>> top = svc.top(2);

        assertEquals(b, top.get(0).getKey());
        assertEquals(a, top.get(1).getKey());
    }
}
