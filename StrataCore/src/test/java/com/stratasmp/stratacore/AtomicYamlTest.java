package com.stratasmp.stratacore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicYamlTest {
    private final Logger log = Logger.getAnonymousLogger();

    @Test
    void savesAndReloads(@TempDir Path dir) throws Exception {
        File file = dir.resolve("balances.yml").toFile();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("abc", 42);
        AtomicYaml.save(yaml, file);

        assertEquals(42, AtomicYaml.load(file, log).getInt("abc"));
        assertFalse(new File(dir.toFile(), "balances.yml.tmp").exists(), "temp file must not be left behind");
    }

    @Test
    void saveReplacesAnExistingFile(@TempDir Path dir) throws Exception {
        File file = dir.resolve("data.yml").toFile();
        YamlConfiguration first = new YamlConfiguration();
        first.set("v", 1);
        AtomicYaml.save(first, file);
        YamlConfiguration second = new YamlConfiguration();
        second.set("v", 2);
        AtomicYaml.save(second, file);

        assertEquals(2, AtomicYaml.load(file, log).getInt("v"));
    }

    @Test
    void missingFileGivesAnEmptyConfig(@TempDir Path dir) {
        assertTrue(AtomicYaml.load(dir.resolve("nope.yml").toFile(), log).getKeys(false).isEmpty());
    }

    @Test
    void damagedFileIsMovedAsideNotOverwritten(@TempDir Path dir) throws Exception {
        File file = dir.resolve("charms.yml").toFile();
        Files.writeString(file.toPath(), "this: [is: not: valid: yaml\n  - broken");

        YamlConfiguration loaded = AtomicYaml.load(file, log);

        assertTrue(loaded.getKeys(false).isEmpty());
        assertFalse(file.exists(), "the damaged file must be moved out of the way");
        File[] aside = dir.toFile().listFiles((d, name) -> name.startsWith("charms.yml.corrupt-"));
        assertEquals(1, aside.length);
        assertTrue(Files.readString(aside[0].toPath()).contains("not: valid"), "the original content is kept for recovery");
    }
}
