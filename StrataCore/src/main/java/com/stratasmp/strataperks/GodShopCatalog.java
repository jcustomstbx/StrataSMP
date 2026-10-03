package com.stratasmp.strataperks;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;
import com.stratasmp.stratacore.StrataModule;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Loads {@code god-items.yml} - the /strataperkshop catalog. */
public final class GodShopCatalog {

    private final StrataModule plugin;
    private final List<GodItem> items = new ArrayList<>();

    public GodShopCatalog(StrataModule plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        items.clear();
        File f = new File(plugin.getDataFolder(), "god-items.yml");
        if (!f.exists()) {
            plugin.saveResource("god-items.yml", false);
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = y.getConfigurationSection("items");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection entry = root.getConfigurationSection(id);
            if (entry == null) {
                plugin.getLogger().warning("God item '" + id + "' is not a section, skipping.");
                continue;
            }
            Material material = Material.matchMaterial(entry.getString("material", ""));
            if (material == null) {
                plugin.getLogger().warning("Unknown material for god item '" + id + "', skipping.");
                continue;
            }
            EntityType spawnerType = null;
            String spawnerTypeName = entry.getString("spawner-type", "").trim();
            if (!spawnerTypeName.isEmpty()) {
                try {
                    spawnerType = EntityType.valueOf(spawnerTypeName.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Unknown spawner type for god item '" + id + "', skipping.");
                    continue;
                }
                if (material != Material.SPAWNER) {
                    plugin.getLogger().warning("Spawner type on non-spawner god item '" + id + "', skipping.");
                    continue;
                }
            }
            Map<String, Integer> enchantments = new LinkedHashMap<>();
            ConfigurationSection enchantSection = entry.getConfigurationSection("enchantments");
            if (enchantSection != null) {
                for (String key : enchantSection.getKeys(false)) {
                    enchantments.put(key, enchantSection.getInt(key));
                }
            }
            if (entry.getLong("price", 0) <= 0) {
                plugin.getLogger().warning("God item '" + id + "' has no valid price and was skipped.");
                continue;
            }
            items.add(new GodItem(
                    id,
                    entry.getString("name", id),
                    material,
                    Math.max(1, entry.getInt("amount", 1)),
                    spawnerType,
                    entry.getLong("price", 0),
                    entry.getStringList("lore"),
                    enchantments));
        }
    }

    public List<GodItem> all() {
        return List.copyOf(items);
    }

    /** Builds the real item to hand over - resolved fresh each time so a catalog reload can't affect an already-built stack. */
    public ItemStack build(GodItem item) {
        ItemStack stack = new ItemStack(item.material, item.amount);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Msg.color(item.name));
        if (!item.lore.isEmpty()) {
            meta.lore(item.lore.stream().map(Msg::color).toList());
        }
        for (Map.Entry<String, Integer> e : item.enchantments.entrySet()) {
            Enchantment enchant = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(e.getKey().toLowerCase()));
            if (enchant == null) {
                plugin.getLogger().warning("Unknown enchantment '" + e.getKey() + "' on god item '" + item.id + "'");
                continue;
            }
            meta.addEnchant(enchant, e.getValue(), true);
        }
        if (item.spawnerType != null && meta instanceof BlockStateMeta blockStateMeta) {
            blockStateMeta.getPersistentDataContainer().set(
                    new NamespacedKey("smpsilkspawner", "spawned_type"),
                    PersistentDataType.STRING,
                    item.spawnerType.name());
            BlockState blockState = blockStateMeta.getBlockState();
            if (blockState instanceof CreatureSpawner spawner) {
                spawner.setSpawnedType(item.spawnerType);
                blockStateMeta.setBlockState(spawner);
            } else {
                plugin.getLogger().warning("Couldn't set spawner state for god item '" + item.id + "'.");
            }
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
