package com.stratasmp.stratakits;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import com.stratasmp.stratacore.StrataModule;

import java.io.File;

/**
 * Home and personal-vault allowances per tier, shown on the rank menu. Each number is read from the file that
 * actually enforces it, so the menu can't drift: vaults from StrataVaults' {@code vault-limits}, homes from
 * EssentialsX's {@code sethome-multiple}. Both are keyed tier1 ... tier10.
 */
final class RankLimits {

    private final StrataModule plugin;
    private YamlConfiguration vaults = new YamlConfiguration();
    private YamlConfiguration homes = new YamlConfiguration();

    RankLimits(StrataModule plugin) {
        this.plugin = plugin;
        reload();
    }

    void reload() {
        File plugins = plugin.getDataFolder().getParentFile();
        vaults = load(new File(plugins, "StrataVaults/config.yml"));
        homes = load(new File(plugins, "Essentials/config.yml"));
    }

    private static YamlConfiguration load(File file) {
        return file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    /** 0 when there is no entry for that tier. */
    int homes(String rank) {
        return count(homes, "sethome-multiple", rank);
    }

    int vaults(String rank) {
        return count(vaults, "vault-limits", rank);
    }

    static int count(ConfigurationSection config, String section, String rank) {
        return Math.max(0, config.getInt(section + "." + rank, 0));
    }
}
