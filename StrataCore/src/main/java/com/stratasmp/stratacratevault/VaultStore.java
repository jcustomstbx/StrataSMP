package com.stratasmp.stratacratevault;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import com.stratasmp.stratacore.StrataModule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * One yml file per player under vaults/. Saved on every change - prizes are things players
 * won, so a crash right after a deposit must not lose them. Main-thread only.
 */
final class VaultStore {

    private final StrataModule plugin;
    private final File dir;
    private final Map<UUID, List<VaultEntry>> cache = new HashMap<>();

    VaultStore(StrataModule plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "vaults");
        dir.mkdirs();
    }

    List<VaultEntry> entries(UUID id) {
        return cache.computeIfAbsent(id, this::load);
    }

    int totalItems(UUID id) {
        int total = 0;
        for (VaultEntry e : entries(id)) {
            total += e.amount;
        }
        return total;
    }

    void deposit(UUID id, Material material, int amount, Map<String, Integer> enchants) {
        List<VaultEntry> list = entries(id);
        String key = VaultEntry.keyOf(material, enchants);
        for (VaultEntry e : list) {
            if (e.key().equals(key)) {
                e.amount += amount;
                save(id);
                return;
            }
        }
        list.add(new VaultEntry(material, enchants, amount));
        save(id);
    }

    void save(UUID id) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (VaultEntry e : entries(id)) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("material", e.material.name());
            map.put("amount", e.amount);
            map.put("enchants", new LinkedHashMap<>(e.enchants));
            out.add(map);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("entries", out);
        try {
            yaml.save(fileFor(id));
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save vault for " + id + " - a prize may be lost!", ex);
        }
    }

    private File fileFor(UUID id) {
        return new File(dir, id + ".yml");
    }

    private List<VaultEntry> load(UUID id) {
        List<VaultEntry> list = new ArrayList<>();
        File file = fileFor(id);
        if (!file.exists()) {
            return list;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> map : yaml.getMapList("entries")) {
            Material material = Material.matchMaterial(String.valueOf(map.get("material")));
            if (material == null) {
                plugin.getLogger().warning("Unknown material '" + map.get("material") + "' in " + file.getName() + ", skipping entry.");
                continue;
            }
            int amount = ((Number) map.get("amount")).intValue();
            Map<String, Integer> enchants = new HashMap<>();
            if (map.get("enchants") instanceof Map<?, ?> raw) {
                for (Map.Entry<?, ?> e : raw.entrySet()) {
                    enchants.put(String.valueOf(e.getKey()), ((Number) e.getValue()).intValue());
                }
            }
            list.add(new VaultEntry(material, enchants, amount));
        }
        return list;
    }
}
