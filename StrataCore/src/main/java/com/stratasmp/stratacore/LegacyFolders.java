package com.stratasmp.stratacore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * One-time import of data from the old Milly plugin folders into the matching Strata module folders, so team
 * memberships, kit cooldowns, vaults, charm and perk balances and the like carry over. Each module's own
 * config and message files are not copied (their keys changed with the rename); the bundled defaults are used.
 * Existing files are never overwritten, a marker file records a finished import, and a copy that was interrupted
 * is simply finished on the next start. Old folders are left untouched.
 */
final class LegacyFolders {
    static final String MARKER = ".legacy-imported";
    private static final Map<String, String> OLD_TO_NEW = new LinkedHashMap<>();
    /** Files that are configuration in every module. */
    private static final Set<String> COMMON_CONFIG = Set.of("config.yml", "messages.yml", "special-offers.yml");
    /** Files that are configuration only in one module (the same name is saved player data elsewhere, e.g. duel kits). */
    private static final Map<String, Set<String>> MODULE_CONFIG = Map.of(
            "MillyKits", Set.of("kits.yml"),
            "MillySovereigns", Set.of("god-items.yml"));

    static {
        OLD_TO_NEW.put("MillyTeams", "StrataTeams");
        OLD_TO_NEW.put("MillyHub", "StrataHub");
        OLD_TO_NEW.put("MillyKits", "StrataKits");
        OLD_TO_NEW.put("MillyCrateVault", "StrataCrateVault");
        OLD_TO_NEW.put("MillyCustomWeapons", "StrataWeapons");
        OLD_TO_NEW.put("MillySovereigns", "StrataPerks");
        OLD_TO_NEW.put("MillyMMO", "StrataMMO");
        OLD_TO_NEW.put("MillyTrade", "StrataTrade");
        OLD_TO_NEW.put("MillyDuels", "StrataDuels");
        OLD_TO_NEW.put("MillyVoteReward", "StrataVoteReward");
        OLD_TO_NEW.put("MillyLeaderboards", "StrataLeaderboards");
        OLD_TO_NEW.put("MillyBosses", "StrataBosses");
        OLD_TO_NEW.put("MillyRanks", "StrataRanks");
    }

    private LegacyFolders() {}

    static void migrate(File pluginsDir, Logger log) {
        int found = 0;
        for (Map.Entry<String, String> e : OLD_TO_NEW.entrySet()) {
            File old = new File(pluginsDir, e.getKey());
            File target = new File(pluginsDir, e.getValue());
            if (!old.isDirectory()) continue;
            found++;
            File marker = new File(target, MARKER);
            if (marker.exists()) continue;
            try {
                int copied = copy(old.toPath(), target.toPath(), e.getKey());
                Files.createDirectories(target.toPath());
                Files.writeString(marker.toPath(), "Imported from plugins/" + e.getKey() + "\n");
                if (copied > 0) log.info("Imported " + copied + " data file(s) from plugins/" + e.getKey() + " into plugins/" + e.getValue() + ".");
            } catch (IOException failure) {
                // no marker was written, so the next start finishes the job
                log.warning("Import from plugins/" + e.getKey() + " was interrupted and will be retried: " + failure.getMessage());
            }
        }
        log.info("Legacy import: " + found + " old Milly plugin folder(s) found.");
    }

    private static int copy(Path from, Path to, String oldName) throws IOException {
        Set<String> moduleConfig = MODULE_CONFIG.getOrDefault(oldName, Set.of());
        int[] count = {0};
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path source : (Iterable<Path>) walk::iterator) {
                Path relative = from.relativize(source);
                String top = relative.getNameCount() == 0 ? "" : relative.getName(0).toString();
                boolean rootFile = relative.getNameCount() == 1;
                if (rootFile && (COMMON_CONFIG.contains(top) || moduleConfig.contains(top))) continue;
                if (top.equals("sections") || top.endsWith(".jar")) continue;
                Path dest = to.resolve(relative.toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(dest);
                } else if (!Files.exists(dest)) {
                    Files.createDirectories(dest.getParent());
                    Files.copy(source, dest, StandardCopyOption.COPY_ATTRIBUTES);
                    count[0]++;
                }
            }
        }
        return count[0];
    }
}
