package com.stratasmp.strataweapons;

import org.bukkit.configuration.file.YamlConfiguration;
import com.stratasmp.stratacore.StrataModule;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Account-bound StrataCharm balances. Saves on every change rather than on an interval -
 * charms are sold for real money, so a crash right after a purchase can't be allowed to lose the grant.
 * Main-thread only.
 */
public final class StrataCharmService {

    private final StrataModule plugin;
    private final File file;
    private final YamlConfiguration storage;

    public StrataCharmService(StrataModule plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "charms.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.storage = com.stratasmp.stratacore.AtomicYaml.load(file, plugin.getLogger());
    }

    public int getBalance(UUID uuid) {
        return storage.getInt(uuid.toString(), 0);
    }

    public void deposit(UUID uuid, int amount) {
        storage.set(uuid.toString(), getBalance(uuid) + amount);
        save();
    }

    /** @return false, withdrawing nothing, if the balance is too low. */
    public boolean withdraw(UUID uuid, int amount) {
        int balance = getBalance(uuid);
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
            plugin.getLogger().log(Level.SEVERE, "Could not save charms.yml - a StrataCharm balance change may be lost!", e);
        }
    }
}
