package com.stratasmp.stratacore;

import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Writes a YAML snapshot off the main thread. Hand it a fresh copy of the data (never the live object); a slow write can
 * never overwrite a newer one. Falls back to writing in place while the module is shutting down.
 */
public final class AsyncYamlSaver {
    private final StrataModule plugin;
    private final File file;
    private long submitted;
    private long written;

    public AsyncYamlSaver(StrataModule plugin, File file) {
        this.plugin = plugin;
        this.file = file;
    }

    /** Call from the main thread only. */
    public void save(YamlConfiguration snapshot) {
        long sequence = ++submitted;
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin.hostPlugin(), () -> write(sequence, snapshot));
        } else {
            write(sequence, snapshot);
        }
    }

    private synchronized void write(long sequence, YamlConfiguration snapshot) {
        if (sequence < written) return;
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            AtomicYaml.save(snapshot, file);
            written = sequence;
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save " + file.getName() + ": " + e.getMessage());
        }
    }
}
