package com.stratasmp.stratakits;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import com.stratasmp.stratacore.StrataModule;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Level;

/** When each player last claimed each kit. Cooldowns are per kit, so ranking up unlocks the new kit straight away. */
final class ClaimStore {

    private final StrataModule plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final Object writeLock = new Object();

    ClaimStore(StrataModule plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claims.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    long lastClaim(UUID id, String kit) {
        return yaml.getLong(id + "." + kit, 0L);
    }

    void record(UUID id, String kit, long millis) {
        yaml.set(id + "." + kit, millis);
        save();
    }

    void reset(UUID id, String kit) {
        yaml.set(kit == null ? id.toString() : id + "." + kit, null);
        save();
    }

    private void save() {
        String snapshot = yaml.saveToString();
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(snapshot));
        } else {
            write(snapshot);
        }
    }

    private void write(String data) {
        synchronized (writeLock) {
            try {
                Files.writeString(file.toPath(), data, StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save claims.yml - cooldowns may reset!", e);
            }
        }
    }
}