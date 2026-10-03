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
    private final StrataModule plugin;

    public PendingRestores(StrataModule plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "pending-restores");
    }

    public void save(UUID uuid, PlayerSnapshot snapshot) {
        if (!dir.exists()) {
            dir.mkdirs();
        }
        YamlConfiguration cfg = new YamlConfiguration();
        snapshot.saveTo(cfg);
        try {
            // temp file + move: a crash mid-write (the case this file exists for) must not leave a truncated copy
            com.stratasmp.stratacore.AtomicYaml.save(cfg, file(uuid));
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't save pending duel restore for " + uuid, e);
        }
    }

    /** Safety copy taken when a match starts, so a crash can't leave the kit as someone's only inventory. */
    public void saveQuietly(UUID uuid, PlayerSnapshot snapshot) {
        try {
            save(uuid, snapshot);
        } catch (IllegalStateException e) {
            // best effort only; the in-memory snapshot is still the primary copy
        }
    }

    /** The match ended cleanly and the snapshot was applied, so the safety copy goes. */
    public void discard(UUID uuid) {
        file(uuid).delete();
    }

    public PlayerSnapshot takeIfPresent(UUID uuid) {
        File f = file(uuid);
        if (!f.isFile()) {
            return null;
        }
        PlayerSnapshot snapshot;
        try {
            snapshot = PlayerSnapshot.loadFrom(YamlConfiguration.loadConfiguration(f));
        } catch (RuntimeException e) {
            snapshot = null;
        }
        if (snapshot == null || !snapshot.isUsable()) {
            // a damaged copy would wipe the inventory and set health to 0, so it is set aside instead of applied
            File aside = new File(dir, uuid + ".yml.corrupt-" + System.currentTimeMillis());
            f.renameTo(aside);
            plugin.getLogger().severe("Pending duel restore for " + uuid + " is damaged; moved to " + aside.getName());
            return null;
        }
        f.delete();
        return snapshot;
    }

    private File file(UUID uuid) {
        return new File(dir, uuid + ".yml");
    }
}
