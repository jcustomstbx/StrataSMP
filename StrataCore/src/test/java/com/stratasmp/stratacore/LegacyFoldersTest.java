package com.stratasmp.stratacore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyFoldersTest {
    private final Logger log = Logger.getAnonymousLogger();

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void importsDataButNotConfig(@TempDir Path plugins) throws Exception {
        write(plugins.resolve("MillyTeams/teams.yml"), "teams: data");
        write(plugins.resolve("MillyTeams/config.yml"), "old: config");
        write(plugins.resolve("MillyTeams/playerdata/a.yml"), "x: 1");

        LegacyFolders.migrate(plugins.toFile(), log);

        assertEquals("teams: data", Files.readString(plugins.resolve("StrataTeams/teams.yml")));
        assertEquals("x: 1", Files.readString(plugins.resolve("StrataTeams/playerdata/a.yml")));
        assertFalse(Files.exists(plugins.resolve("StrataTeams/config.yml")), "config files are not imported");
        assertTrue(Files.exists(plugins.resolve("StrataTeams/" + LegacyFolders.MARKER)));
    }

    @Test
    void kitsYmlIsConfigForKitsButDataForDuels(@TempDir Path plugins) throws Exception {
        write(plugins.resolve("MillyKits/kits.yml"), "kit config");
        write(plugins.resolve("MillyDuels/kits.yml"), "saved duel kits");

        LegacyFolders.migrate(plugins.toFile(), log);

        assertFalse(Files.exists(plugins.resolve("StrataKits/kits.yml")));
        assertEquals("saved duel kits", Files.readString(plugins.resolve("StrataDuels/kits.yml")));
    }

    @Test
    void godItemsAreConfigForPerksOnly(@TempDir Path plugins) throws Exception {
        write(plugins.resolve("MillySovereigns/god-items.yml"), "items");
        write(plugins.resolve("MillySovereigns/balances.yml"), "abc: 5");

        LegacyFolders.migrate(plugins.toFile(), log);

        assertFalse(Files.exists(plugins.resolve("StrataPerks/god-items.yml")));
        assertEquals("abc: 5", Files.readString(plugins.resolve("StrataPerks/balances.yml")));
    }

    @Test
    void neverOverwritesExistingFiles(@TempDir Path plugins) throws Exception {
        write(plugins.resolve("MillyKits/claims.yml"), "old claims");
        write(plugins.resolve("StrataKits/claims.yml"), "new claims");

        LegacyFolders.migrate(plugins.toFile(), log);

        assertEquals("new claims", Files.readString(plugins.resolve("StrataKits/claims.yml")));
    }

    @Test
    void finishedImportIsNotRepeated(@TempDir Path plugins) throws Exception {
        write(plugins.resolve("MillyHub/hub.yml"), "first");
        LegacyFolders.migrate(plugins.toFile(), log);
        Files.delete(plugins.resolve("StrataHub/hub.yml"));
        write(plugins.resolve("MillyHub/other.yml"), "added later");

        LegacyFolders.migrate(plugins.toFile(), log);

        assertFalse(Files.exists(plugins.resolve("StrataHub/hub.yml")), "the marker stops a second import");
        assertFalse(Files.exists(plugins.resolve("StrataHub/other.yml")));
    }

    @Test
    void interruptedImportIsFinishedOnTheNextStart(@TempDir Path plugins) throws Exception {
        write(plugins.resolve("MillyCrateVault/vaults.yml"), "vaults");
        write(plugins.resolve("MillyCrateVault/extra.yml"), "extra");
        // an earlier run copied one file and died before writing the marker
        write(plugins.resolve("StrataCrateVault/vaults.yml"), "vaults");

        LegacyFolders.migrate(plugins.toFile(), log);

        assertEquals("extra", Files.readString(plugins.resolve("StrataCrateVault/extra.yml")));
        assertTrue(Files.exists(plugins.resolve("StrataCrateVault/" + LegacyFolders.MARKER)));
    }

    @Test
    void missingOldFoldersAreIgnored(@TempDir Path plugins) {
        LegacyFolders.migrate(plugins.toFile(), log);
        assertFalse(Files.exists(plugins.resolve("StrataTeams")));
    }
}
