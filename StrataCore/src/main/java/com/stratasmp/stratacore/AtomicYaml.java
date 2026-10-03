package com.stratasmp.stratacore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Crash-safe YAML storage for files that hold balances: write to a temp file, then move it into place. */
public final class AtomicYaml {
    private AtomicYaml() {}

    /**
     * Loads the file. If it exists but cannot be parsed it is moved aside as *.corrupt (never overwritten by the next
     * save) and an empty config is returned, so a damaged file can be recovered by hand.
     */
    public static YamlConfiguration load(File file, Logger log) {
        YamlConfiguration yaml = new YamlConfiguration();
        if (!file.exists()) return yaml;
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            File aside = new File(file.getParentFile(), file.getName() + ".corrupt-" + System.currentTimeMillis());
            log.severe(file.getName() + " could not be read (" + e.getMessage() + "); moved to " + aside.getName()
                    + ". Restore it by hand if it held balances.");
            try {
                Files.move(file.toPath(), aside.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveFailure) {
                log.severe("Could not move the damaged file aside: " + moveFailure.getMessage());
            }
            return new YamlConfiguration();
        }
        return yaml;
    }

    public static void save(YamlConfiguration yaml, File file) throws IOException {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        yaml.save(temp);
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
