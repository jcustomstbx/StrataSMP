package com.stratasmp.strataeconomy.currency;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

public final class Database {

    private final javax.sql.DataSource pool;

    public Database(ConfigurationSection db, Logger log) {
        String host = db.getString("host");
        int port = db.getInt("port", 3306);
        String name = db.getString("name");
        String user = db.getString("user");
        String pass = db.getString("password");

        HikariConfig hc = new HikariConfig();
        hc.setPoolName("StrataEconomy");
        // driver is shaded + relocated, so point Hikari straight at the moved class
        // instead of relying on the jdbc:mariadb: scheme lookup via DriverManager
        hc.setDriverClassName("com.stratasmp.stratacore.lib.mariadb.Driver");
        hc.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + name
                + "?useUnicode=true&characterEncoding=utf8&autoReconnect=true");
        hc.setUsername(user);
        hc.setPassword(pass);
        hc.setMaximumPoolSize(Math.max(2, db.getInt("pool-size", 6)));
        hc.setConnectionTimeout(8_000);
        hc.setKeepaliveTime(60_000);
        hc.setMaxLifetime(600_000);
        hc.addDataSourceProperty("cachePrepStmts", "true");
        hc.addDataSourceProperty("prepStmtCacheSize", "128");

        this.pool = new HikariDataSource(hc);
        createTables();
        log.info("Database pool up (" + host + "/" + name + ").");
    }

    /** For tests: wraps an existing data source (the caller creates the tables). */
    public Database(javax.sql.DataSource source) {
        this.pool = source;
    }

    private void createTables() {
        try (Connection c = pool.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS stratas_balances (
                        uuid       CHAR(36)    NOT NULL PRIMARY KEY,
                        username   VARCHAR(16) NOT NULL DEFAULT '',
                        balance    BIGINT      NOT NULL DEFAULT 0,
                        updated_at BIGINT      NOT NULL DEFAULT 0,
                        INDEX idx_balance (balance DESC)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS stratas_auctions (
                        id          CHAR(36)     NOT NULL PRIMARY KEY,
                        seller      CHAR(36)     NOT NULL,
                        seller_name VARCHAR(16)  NOT NULL DEFAULT '',
                        item_b64    MEDIUMTEXT   NOT NULL,
                        price       BIGINT       NOT NULL,
                        listed_at   BIGINT       NOT NULL,
                        featured_until BIGINT NOT NULL DEFAULT 0,
                        INDEX idx_seller (seller)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            try (var columns = c.getMetaData().getColumns(c.getCatalog(), null, "stratas_auctions", "featured_until")) {
                if (!columns.next()) {
                    s.executeUpdate("ALTER TABLE stratas_auctions ADD COLUMN featured_until BIGINT NOT NULL DEFAULT 0");
                }
            }
            s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS stratas_buy_orders (
                        id              CHAR(36)     NOT NULL PRIMARY KEY,
                        buyer           CHAR(36)     NOT NULL,
                        buyer_name      VARCHAR(16)  NOT NULL DEFAULT '',
                        material        VARCHAR(64)  NOT NULL,
                        amount          INT          NOT NULL,
                        price_per_item  BIGINT       NOT NULL,
                        created_at      BIGINT       NOT NULL,
                        INDEX idx_buyer (buyer)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS stratas_buy_order_deliveries (
                        id            CHAR(36)     NOT NULL PRIMARY KEY,
                        buyer         CHAR(36)     NOT NULL,
                        material      VARCHAR(64)  NOT NULL,
                        amount        INT          NOT NULL,
                        delivered_at  BIGINT       NOT NULL,
                        INDEX idx_buyer (buyer)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS stratas_auction_deliveries (
                        id            CHAR(36)     NOT NULL PRIMARY KEY,
                        buyer         CHAR(36)     NOT NULL,
                        item_b64      MEDIUMTEXT   NOT NULL,
                        delivered_at  BIGINT       NOT NULL,
                        INDEX idx_buyer (buyer)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create StrataEconomy tables", e);
        }
    }

    public Connection connection() throws SQLException {
        return pool.getConnection();
    }

    @FunctionalInterface
    public interface TransactionWork {
        void run(Connection connection) throws SQLException;
    }

    public void transaction(TransactionWork work) throws SQLException {
        try (Connection c = connection()) {
            boolean oldAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                work.run(c);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                try {
                    c.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            } finally {
                try {
                    c.setAutoCommit(oldAutoCommit);
                } catch (SQLException ignored) {
                    // Closing the connection below returns it to Hikari for cleanup.
                }
            }
        }
    }

    public void close() {
        if (pool instanceof HikariDataSource hikari && !hikari.isClosed()) {
            hikari.close();
        }
    }
}
