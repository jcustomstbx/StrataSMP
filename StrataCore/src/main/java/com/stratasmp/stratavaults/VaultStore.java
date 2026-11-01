package com.stratasmp.stratavaults;

import com.stratasmp.stratacore.AtomicYaml;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Personal vault contents, one file per player under vaults/. Each vault is stored as Paper's own item bytes
 * (base64), so custom names, enchants and data components survive. Reads and writes run on one background thread;
 * the cache is only changed from the main thread, and every save writes a snapshot, so a crash loses at most the
 * last tick of changes.
 */
final class VaultStore {

    static final int SIZE = 54;

    private final StrataModule plugin;
    private final File dir;
    private final Map<UUID, Map<Integer, String>> cache = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "StrataVaults-io");
        t.setDaemon(true);
        return t;
    });

    VaultStore(StrataModule plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "vaults");
        dir.mkdirs();
    }

    boolean isLoaded(UUID id) {
        return cache.containsKey(id);
    }

    /** Loads a player's vaults into the cache in the background, unless they have left by the time it runs. */
    void preload(UUID id, BooleanSupplier stillWanted) {
        io.execute(() -> {
            if (stillWanted.getAsBoolean() && !cache.containsKey(id)) {
                cache.put(id, read(id));
            }
        });
    }

    /** Drops a player's cache once every earlier write has finished (writes run first, in order). */
    void evict(UUID id) {
        io.execute(() -> cache.remove(id));
    }

    /** Reads an offline player's vaults without caching them; the callback runs on the main thread. */
    void readOffline(UUID id, Consumer<Map<Integer, String>> done) {
        io.execute(() -> {
            Map<Integer, String> data = read(id);
            plugin.getServer().getScheduler().runTask(plugin.hostPlugin(), () -> done.accept(data));
        });
    }

    /** The vault's items, or null when the player isn't loaded or the stored data can't be read. */
    ItemStack[] get(UUID id, int number) {
        Map<Integer, String> data = cache.get(id);
        return data == null ? null : decode(data.get(number), id, number);
    }

    /** Decodes stored vault text; null means unreadable, so the caller never overwrites it with an empty vault. */
    ItemStack[] decode(String encoded, UUID id, int number) {
        ItemStack[] out = new ItemStack[SIZE];
        if (encoded == null || encoded.isEmpty()) {
            return out;
        }
        try {
            ItemStack[] items = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(encoded));
            for (int i = 0; i < items.length && i < SIZE; i++) {
                out[i] = items[i] == null || items[i].getType().isAir() ? null : items[i];
            }
            return out;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Vault " + number + " of " + id + " could not be read; it is left untouched on disk.", e);
            return null;
        }
    }

    /** Stores a vault's contents and writes the player's file in the background. Main thread only. */
    void set(UUID id, int number, ItemStack[] contents) {
        Map<Integer, String> data = cache.get(id);
        if (data == null) {
            plugin.getLogger().severe("Vault " + number + " of " + id + " was changed before it loaded; not saved.");
            return;
        }
        boolean empty = true;
        for (ItemStack stack : contents) {
            if (stack != null && !stack.getType().isAir()) {
                empty = false;
                break;
            }
        }
        if (empty) {
            data.remove(number);
        } else {
            data.put(number, Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(contents)));
        }
        Map<Integer, String> snapshot = new HashMap<>(data);
        io.execute(() -> write(id, snapshot));
    }

    /** Waits for queued writes; call on shutdown. */
    void shutdown() {
        io.shutdown();
        try {
            if (!io.awaitTermination(30, TimeUnit.SECONDS)) {
                plugin.getLogger().severe("Vault writes were still running after 30 seconds.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private File fileFor(UUID id) {
        return new File(dir, id + ".yml");
    }

    private Map<Integer, String> read(UUID id) {
        Map<Integer, String> out = new HashMap<>();
        YamlConfiguration yaml = AtomicYaml.load(fileFor(id), plugin.getLogger());
        ConfigurationSection section = yaml.getConfigurationSection("vaults");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    out.put(Integer.parseInt(key), section.getString(key, ""));
                } catch (NumberFormatException ignored) {
                    // not a vault number
                }
            }
        }
        return out;
    }

    private void write(UUID id, Map<Integer, String> data) {
        YamlConfiguration yaml = new YamlConfiguration();
        data.forEach((number, encoded) -> yaml.set("vaults." + number, encoded));
        try {
            AtomicYaml.save(yaml, fileFor(id));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save the vaults of " + id + " - changes may be lost!", e);
        }
    }
}
