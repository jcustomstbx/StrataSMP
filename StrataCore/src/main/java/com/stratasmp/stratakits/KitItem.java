package com.stratasmp.stratakits;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

record KitItem(Material material, int amount, String name, List<String> lore, Map<String, Integer> enchants) {

    /** Splits into as many stacks as the material allows - totems, for one, don't stack. */
    List<ItemStack> build(Logger log) {
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = amount;
        while (remaining > 0) {
            int chunk = Math.min(remaining, material.getMaxStackSize());
            stacks.add(single(chunk, log));
            remaining -= chunk;
        }
        return stacks;
    }

    int stackCount() {
        return (int) Math.ceil(amount / (double) material.getMaxStackSize());
    }

    private ItemStack single(int count, Logger log) {
        ItemStack stack = new ItemStack(material, count);
        ItemMeta meta = stack.getItemMeta();
        if (name != null && !name.isEmpty()) {
            meta.displayName(Text.item(name));
        }
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(Text::item).toList());
        }
        for (Map.Entry<String, Integer> e : enchants.entrySet()) {
            Enchantment enchant = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(e.getKey().toLowerCase()));
            if (enchant == null) {
                log.warning("Unknown enchantment '" + e.getKey() + "' on a kit " + material.name());
                continue;
            }
            meta.addEnchant(enchant, e.getValue(), true);
        }
        stack.setItemMeta(meta);
        return stack;
    }
}