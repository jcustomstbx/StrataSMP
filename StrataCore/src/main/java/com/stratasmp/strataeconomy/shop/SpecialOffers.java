package com.stratasmp.strataeconomy.shop;

import com.stratasmp.strataeconomy.StrataEconomy;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Loads {@code special-offers.yml} - command-granted shop purchases that aren't a plain {@link Material}. */
public final class SpecialOffers {

    private final StrataEconomy plugin;
    private final List<SpecialOffer> offers = new ArrayList<>();

    public SpecialOffers(StrataEconomy plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        offers.clear();
        File f = new File(plugin.getDataFolder(), "special-offers.yml");
        if (!f.exists()) {
            plugin.saveResource("special-offers.yml", false);
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = y.getConfigurationSection("offers");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection entry = root.getConfigurationSection(id);
            Material icon = Material.matchMaterial(entry.getString("material", "STONE"));
            if (icon == null) {
                plugin.getLogger().warning("Unknown material for special offer '" + id + "', skipping.");
                continue;
            }
            offers.add(new SpecialOffer(
                    id,
                    entry.getString("name", id),
                    icon,
                    entry.getLong("price", 0),
                    entry.getStringList("lore"),
                    entry.getString("command", "")));
        }
    }

    public List<SpecialOffer> all() {
        return List.copyOf(offers);
    }
}
