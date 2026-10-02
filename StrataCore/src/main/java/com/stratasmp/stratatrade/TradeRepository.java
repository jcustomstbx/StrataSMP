/*
 * Copyright (c) 2026 JCustoms. All Rights Reserved.
 *
 * This file is proprietary and confidential. No use, copying, modification,
 * or distribution of this file or its compiled output, by any means, is
 * permitted without the prior written permission of JCustoms.
 *
 * See the LICENSE file distributed with this project for the full terms.
 */
package com.stratasmp.stratatrade;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Escrow rows hold whatever a player has put into an open trade so a crash can't lose it.
 * Every write goes through one thread, in order, so a late save can never resurrect a deleted row.
 */
public final class TradeRepository {

    public record LogEntry(Instant when, String firstName, String secondName, String firstGave, String secondGave,
                           int firstCoins, int secondCoins) {}

    private final DataSource dataSource;
    private final Logger logger;
    private final ExecutorService queue = Executors.newSingleThreadExecutor(runnable -> {
        var thread = new Thread(runnable, "StrataTrade-db");
        thread.setDaemon(true);
        return thread;
    });

    public TradeRepository(DataSource dataSource, Logger logger) {
        this.dataSource = dataSource;
        this.logger = logger;
    }

    public void ensureSchema() {
        var escrow = """
            CREATE TABLE IF NOT EXISTS strata_trade_escrow (
              player_uuid CHAR(36) PRIMARY KEY,
              item_data   MEDIUMBLOB NOT NULL,
              updated_at  DATETIME NOT NULL
            )
            """;
        var log = """
            CREATE TABLE IF NOT EXISTS strata_trade_log (
              id           BIGINT AUTO_INCREMENT PRIMARY KEY,
              traded_at    DATETIME NOT NULL,
              first_uuid   CHAR(36) NOT NULL,
              first_name   VARCHAR(32) NOT NULL,
              second_uuid  CHAR(36) NOT NULL,
              second_name  VARCHAR(32) NOT NULL,
              first_gave   TEXT NOT NULL,
              second_gave  TEXT NOT NULL,
              first_stratas  INT NOT NULL,
              second_stratas INT NOT NULL,
              INDEX idx_trade_first (first_uuid),
              INDEX idx_trade_second (second_uuid)
            )
            """;
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(escrow);
            statement.execute(log);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the trade tables", e);
        }
    }

    public void submit(Runnable task) {
        queue.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "A trade database task failed", e);
            }
        });
    }

    /** Lets queued writes finish, used when the plugin unloads so returned items are also cleared from escrow. */
    public void shutdown() {
        queue.shutdown();
        try {
            if (!queue.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Trade database writes were still pending at shutdown.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void saveEscrow(UUID playerId, byte[] items) {
        var sql = """
            INSERT INTO strata_trade_escrow (player_uuid, item_data, updated_at) VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE item_data = VALUES(item_data), updated_at = VALUES(updated_at)
            """;
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setBytes(2, items);
            statement.setTimestamp(3, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not save trade escrow for " + playerId, e);
        }
    }

    public void deleteEscrow(UUID playerId) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("DELETE FROM strata_trade_escrow WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not clear trade escrow for " + playerId, e);
        }
    }

    public Optional<byte[]> loadEscrow(UUID playerId) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT item_data FROM strata_trade_escrow WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(resultSet.getBytes(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not read trade escrow for " + playerId, e);
            return Optional.empty();
        }
    }

    public void logTrade(UUID firstId, String firstName, UUID secondId, String secondName, String firstGave,
                         String secondGave, int firstCoins, int secondCoins) {
        var sql = """
            INSERT INTO strata_trade_log (traded_at, first_uuid, first_name, second_uuid, second_name,
              first_gave, second_gave, first_stratas, second_stratas)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(Instant.now()));
            statement.setString(2, firstId.toString());
            statement.setString(3, firstName);
            statement.setString(4, secondId.toString());
            statement.setString(5, secondName);
            statement.setString(6, firstGave);
            statement.setString(7, secondGave);
            statement.setInt(8, firstCoins);
            statement.setInt(9, secondCoins);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not write the trade log", e);
        }
    }

    public List<LogEntry> recentFor(UUID playerId, int limit) {
        var sql = """
            SELECT traded_at, first_name, second_name, first_gave, second_gave, first_stratas, second_stratas
            FROM strata_trade_log WHERE first_uuid = ? OR second_uuid = ? ORDER BY id DESC LIMIT ?
            """;
        var results = new ArrayList<LogEntry>();
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, playerId.toString());
            statement.setInt(3, limit);
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(new LogEntry(resultSet.getTimestamp("traded_at").toInstant(),
                        resultSet.getString("first_name"), resultSet.getString("second_name"),
                        resultSet.getString("first_gave"), resultSet.getString("second_gave"),
                        resultSet.getInt("first_stratas"), resultSet.getInt("second_stratas")));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not read the trade log", e);
        }
        return results;
    }
}
