package com.stratasmp.stratakits;

import org.bukkit.configuration.file.YamlConfiguration;
import com.stratasmp.stratacore.StrataModule;

import java.io.File;

/** Home and vault allowances per rank, read from RankEssentials' own config so the rank menu can't drift from what the rank really gives. */
final class RankLimits {

    private final StrataModule plugin;
    private YamlConfiguration config = new YamlConfiguration();

    RankLimits(StrataModule plugin) {
        this.plugin = plugin;
        reload();
    }

    void reload() {
        File file = new File(plugin.getDataFolder().getParentFile(), "RankEssentials/config.yml");
        config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    /** 0 when RankEssentials has no entry for that rank. */
    int homes(String rank) {
        return config.getInt("home-limits." + rank, 0);
    }

    int vaults(String rank) {
        return config.getInt("vault-limits." + rank, 0);
    }
}
