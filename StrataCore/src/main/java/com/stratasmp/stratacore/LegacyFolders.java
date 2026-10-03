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
 * One-time copy of data from the old Milly plugin folders into the matching Strata module folders, so team memberships,
 * kit cooldowns, vaults, charm and perk balances and the like carry over. Config and message files are not copied
 * (their keys changed with the rename); the bundled defaults are used instead. Old folders are left untouched.
 */
final class LegacyFolders {
    private static final Map<String, String> OLD_TO_NEW = new LinkedHashMap<>();
    private static final Set<String> CONFIG_FILES = Set.of(
            "config.yml", "messages.yml", "kits.yml", "god-items.yml", "special-offers.yml", "quests.yml");

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
        for (Map.Entry<String, String> e : OLD_TO_NEW.entrySet()) {
            File old = new File(pluginsDir, e.getKey());
            File target = new File(pluginsDir, e.getValue());
            if (!old.isDirectory()) continue;
            String[] existing = target.list();
            if (existing != null && existing.length > 0) continue; // already has data: never overwrite
            try {
                int copied = copy(old.toPath(), target.toPath());
                if (copied > 0) log.info("Imported " + copied + " data file(s) from plugins/" + e.getKey() + " into plugins/" + e.getValue() + ".");
            } catch (IOException failure) {
                log.warning("Could not import plugins/" + e.getKey() + ": " + failure.getMessage());
            }
        }
    }

    private static int copy(Path from, Path to) throws IOException {
        int[] count = {0};
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path source : (Iterable<Path>) walk::iterator) {
                Path relative = from.relativize(source);
                String top = relative.getNameCount() == 0 ? "" : relative.getName(0).toString();
                if (relative.getNameCount() == 1 && CONFIG_FILES.contains(top)) continue;
                if (top.equals("sections") || top.endsWith(".jar")) continue;
                Path dest = to.resolve(relative.toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(source, dest, StandardCopyOption.COPY_ATTRIBUTES);
                    count[0]++;
                }
            }
        }
        return count[0];
    }
}
