package com.stratasmp.strataduels.match;

import org.bukkit.configuration.file.YamlConfiguration;
import com.stratasmp.stratacore.StrataModule;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * A duelist's pre-match snapshot that couldn't be applied because they'd
 * already disconnected (PlayerSnapshot.applyState only runs a tick after
 * resolveMatch, by which point a quitting player has stopped being online).
 * Without this, whatever the kit gave them stays as their live inventory
 * forever, and their real pre-duel items are gone - both a duplication and
 * an item-loss bug. Saved here on disconnect, applied on their next join.
 */
public final class PendingRestores {

    private final File dir;

    public PendingRestores(StrataModule plugin) {
        this.dir = new File(plugin.getDataFolder(), "pending-restores");
    }

    public void save(UUID uuid, PlayerSnapshot snapshot) {
        if (!dir.exists()) {
            dir.mkdirs();
        }
        YamlConfiguration cfg = new YamlConfiguration();
        snapshot.saveTo(cfg);
        try {
            cfg.save(file(uuid));
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't save pending duel restore for " + uuid, e);
        }
    }

    public PlayerSnapshot takeIfPresent(UUID uuid) {
        File f = file(uuid);
        if (!f.isFile()) {
            return null;
        }
        PlayerSnapshot snapshot = PlayerSnapshot.loadFrom(YamlConfiguration.loadConfiguration(f));
        f.delete();
        return snapshot;
    }

    private File file(UUID uuid) {
        return new File(dir, uuid + ".yml");
    }
}
