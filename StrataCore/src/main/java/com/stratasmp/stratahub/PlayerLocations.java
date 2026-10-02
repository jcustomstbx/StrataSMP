package com.stratasmp.stratahub;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import com.stratasmp.stratacore.StrataModule;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.Map;

/**
 * A player's last known position in the SMP world - kept fresh whenever they
 * leave it (see SmpLocationTracker), and read by the entry NPC to decide
 * between a fresh RTP (never been in) and a return trip (been in before).
 */
public final class PlayerLocations {

    private final StrataModule plugin;
    private final File dir;
    private final Map<UUID, Location> cache = new ConcurrentHashMap<>();

    public PlayerLocations(StrataModule plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "playerdata");
    }

    public Location get(UUID uuid) {
        Location cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }
        File f = file(uuid);
        if (!f.isFile()) {
            return null;
        }
        Location loaded = (Location) YamlConfiguration.loadConfiguration(f).get("location");
        if (loaded != null) {
            cache.put(uuid, loaded);
        }
        return loaded;
    }

    public boolean hasEnteredBefore(UUID uuid) {
        return get(uuid) != null;
    }

    public void set(UUID uuid, Location location) {
        cache.put(uuid, location.clone());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("location", location);
        try {
            cfg.save(file(uuid));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Couldn't save last SMP location for " + uuid, e);
        }
    }

    private File file(UUID uuid) {
        return new File(dir, uuid + ".yml");
    }
}
