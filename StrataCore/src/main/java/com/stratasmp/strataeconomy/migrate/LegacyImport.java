package com.stratasmp.strataeconomy.migrate;

import com.stratasmp.strataeconomy.StrataEconomy;
import com.stratasmp.strataeconomy.currency.Database;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One-shot import of balances from the old plugins into stratas_balances.
 * RankEssentials/economy.yml is the source of truth (its StratasEconomyProvider
 * was the live Vault economy); StratasPlugin/balances.yml is a fallback.
 * Leaves a .migrated marker so it never runs twice.
 */
public final class LegacyImport {

    private final StrataEconomy plugin;
    private final Database db;

    public LegacyImport(StrataEconomy plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void runIfNeeded() {
        File marker = new File(plugin.getDataFolder(), ".migrated");
        if (marker.exists()) {
            return;
        }
        ConfigurationSection dbc = plugin.getConfig().getConfigurationSection("migration");
        if (dbc == null || !dbc.getBoolean("from-rankessentials", true)) {
            writeMarker(marker, "skipped");
            return;
        }

        Map<UUID, Long> merged = new LinkedHashMap<>();
        readFlatBalances(new File(plugin.getDataFolder(),
                dbc.getString("stratasplugin-balances-file", "../StratasPlugin/balances.yml")), merged);
        // RankEssentials wins on conflict - it was the authoritative store
        readFlatBalances(new File(plugin.getDataFolder(),
                dbc.getString("rankessentials-economy-file", "../RankEssentials/economy.yml")), merged);

        if (merged.isEmpty()) {
            plugin.getLogger().info("Migration: nothing to import.");
            writeMarker(marker, "empty");
            return;
        }

        int imported = 0;
        String sql = "INSERT INTO stratas_balances (uuid, username, balance, updated_at) VALUES (?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE balance=VALUES(balance)";
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Long> e : merged.entrySet()) {
                ps.setString(1, e.getKey().toString());
                ps.setString(2, "");
                ps.setLong(3, Math.max(0L, e.getValue()));
                ps.setLong(4, now);
                ps.addBatch();
                imported++;
            }
            ps.executeBatch();
        } catch (Exception ex) {
            plugin.getLogger().severe("Migration failed, NOT marking done: " + ex.getMessage());
            return;
        }
        plugin.getLogger().info("Migration: imported " + imported + " balances into stratas_balances.");
        writeMarker(marker, "imported " + imported + " @ " + System.currentTimeMillis());
    }

    private void readFlatBalances(File f, Map<UUID, Long> into) {
        if (f == null || !f.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        for (String key : y.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                into.put(id, y.getLong(key, 0L));
            } catch (IllegalArgumentException ignored) {
                // non-uuid keys (config sections etc.) - skip
            }
        }
    }

    private void writeMarker(File marker, String note) {
        try {
            Files.writeString(marker.toPath(), note);
        } catch (Exception ignored) {
        }
    }
}
