package com.stratasmp.strataperks;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Map;

/** One /strataperkshop catalog entry - a fully enchanted item granted directly on purchase. */
public final class GodItem {

    public final String id;
    public final String name;
    public final Material material;
    public final int amount;
    public final EntityType spawnerType;
    public final long price;
    public final List<String> lore;
    public final Map<String, Integer> enchantments;

    public GodItem(String id, String name, Material material, int amount, EntityType spawnerType, long price,
                   List<String> lore, Map<String, Integer> enchantments) {
        this.id = id;
        this.name = name;
        this.material = material;
        this.amount = amount;
        this.spawnerType = spawnerType;
        this.price = price;
        this.lore = lore;
        this.enchantments = enchantments;
    }
}
