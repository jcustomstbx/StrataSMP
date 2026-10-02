package com.stratasmp.stratakits;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import com.stratasmp.stratacore.StrataModule;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kits load in the order they appear in kits.yml, lowest rank first. A kit's id is also the
 * LuckPerms group that unlocks it, and a player always gets the kit of the highest of those
 * groups they hold.
 */
final class KitCatalog {

    private final StrataModule plugin;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    KitCatalog(StrataModule plugin) {
        this.plugin = plugin;
        reload();
    }

    void reload() {
        kits.clear();
        File file = new File(plugin.getDataFolder(), "kits.yml");
        if (!file.exists()) {
            plugin.saveResource("kits.yml", false);
        }
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("kits");
        if (root == null) {
            plugin.getLogger().warning("kits.yml has no 'kits' section.");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section != null) {
                kits.put(id.toLowerCase(), parse(id.toLowerCase(), section));
            }
        }
        plugin.getLogger().info("Loaded " + kits.size() + " kit(s).");
    }

    private Kit parse(String id, ConfigurationSection section) {
        List<KitItem> items = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("items")) {
            Material material = Material.matchMaterial(String.valueOf(raw.get("material")));
            if (material == null || !material.isItem()) {
                plugin.getLogger().warning("Kit '" + id + "': unknown material '" + raw.get("material") + "', skipped.");
                continue;
            }
            int amount = raw.get("amount") instanceof Number n ? n.intValue() : 1;
            String name = raw.get("name") == null ? "" : String.valueOf(raw.get("name"));
            List<String> lore = new ArrayList<>();
            if (raw.get("lore") instanceof List<?> lines) {
                lines.forEach(line -> lore.add(String.valueOf(line)));
            }
            Map<String, Integer> enchants = new LinkedHashMap<>();
            if (raw.get("enchants") instanceof Map<?, ?> map) {
                map.forEach((k, v) -> enchants.put(String.valueOf(k), ((Number) v).intValue()));
            }
            items.add(new KitItem(material, Math.max(1, amount), name, lore, enchants));
        }
        List<Reward> rewards = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("rewards")) {
            Material icon = Material.matchMaterial(String.valueOf(raw.get("icon")));
            rewards.add(new Reward(String.valueOf(raw.get("display")), icon == null ? Material.CHEST : icon,
                    String.valueOf(raw.get("command"))));
        }
        Material icon = Material.matchMaterial(section.getString("icon", "CHEST"));
        return new Kit(id, section.getString("display-name", id), icon == null ? Material.CHEST : icon, items, rewards);
    }

    /** Lowest rank first. */
    List<Kit> ascending() {
        return List.copyOf(kits.values());
    }

    Kit get(String id) {
        return id == null ? null : kits.get(id.toLowerCase());
    }

    /**
     * The kit for the highest ladder rank the player holds, or null if they hold none. Ops pass
     * hasPermission() for any node nobody has set, so a group only counts when it's explicitly set.
     */
    Kit kitFor(Player player) {
        List<Kit> list = ascending();
        for (int i = list.size() - 1; i >= 0; i--) {
            String node = "group." + list.get(i).id();
            if (player.isPermissionSet(node) && player.hasPermission(node)) {
                return list.get(i);
            }
        }
        return null;
    }
}