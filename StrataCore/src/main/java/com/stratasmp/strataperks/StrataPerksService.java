package com.stratasmp.strataperks;

import org.bukkit.configuration.file.YamlConfiguration;
import com.stratasmp.stratacore.StrataModule;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Account-bound balance store for StrataPerks. Saves on every mutation rather than on an
 * interval - this backs a real-money currency, so a crash right after a store purchase
 * must not be able to lose the grant.
 */
public final class StrataPerksService {

    private final StrataModule plugin;
    private final File file;
    private final YamlConfiguration storage;

    public StrataPerksService(StrataModule plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "balances.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.storage = com.stratasmp.stratacore.AtomicYaml.load(file, plugin.getLogger());
    }

    public long getBalance(UUID uuid) {
        return storage.getLong(uuid.toString(), 0);
    }

    public boolean has(UUID uuid, long amount) {
        return getBalance(uuid) >= amount;
    }

    public void deposit(UUID uuid, long amount) {
        storage.set(uuid.toString(), getBalance(uuid) + amount);
        save();
    }

    /** @return false if the balance was insufficient - nothing is withdrawn in that case. */
    public boolean withdraw(UUID uuid, long amount) {
        long balance = getBalance(uuid);
        if (balance < amount) {
            return false;
        }
        storage.set(uuid.toString(), balance - amount);
        save();
        return true;
    }

    private void save() {
        try {
            com.stratasmp.stratacore.AtomicYaml.save(storage, file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save balances.yml - a StrataPerks balance change may be lost!", e);
        }
    }
}
