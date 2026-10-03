package com.stratasmp.stratacratevault;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.TreeMap;

/** One stack-type in a vault - the same material and enchants merge into a single entry with a total amount. */
final class VaultEntry {

    final Material material;
    final Map<String, Integer> enchants;
    int amount;

    VaultEntry(Material material, Map<String, Integer> enchants, int amount) {
        this.material = material;
        this.enchants = new TreeMap<>(enchants);
        this.amount = amount;
    }

    static String keyOf(Material material, Map<String, Integer> enchants) {
        StringBuilder key = new StringBuilder(material.name());
        new TreeMap<>(enchants).forEach((name, level) -> key.append('|').append(name).append(':').append(level));
        return key.toString();
    }

    String key() {
        return keyOf(material, enchants);
    }

    ItemStack toItem(int quantity) {
        ItemStack item = new ItemStack(material, quantity);
        if (enchants.isEmpty()) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        for (Map.Entry<String, Integer> e : enchants.entrySet()) {
            Enchantment enchant = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(e.getKey()));
            if (enchant != null) {
                // enchanted books hold their enchants as stored ones; addEnchant would make them inert
                if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta storage) {
                    storage.addStoredEnchant(enchant, e.getValue(), true);
                } else {
                    meta.addEnchant(enchant, e.getValue(), true);
                }
            }
        }
        item.setItemMeta(meta);
        return item;
    }
}
