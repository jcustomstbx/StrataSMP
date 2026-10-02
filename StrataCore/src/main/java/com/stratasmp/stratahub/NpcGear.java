package com.stratasmp.stratahub;

import net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

/**
 * Reads a rank's armour and sword out of StrataKits' kits.yml so a showcase NPC can wear the real kit.
 * The items are left unenchanted on purpose: the enchantment glint is a second render pass per piece,
 * and seven NPCs in full gear made clients drop frames when they looked at the row.
 */
final class NpcGear {

    private final StrataHub plugin;

    NpcGear(StrataHub plugin) {
        this.plugin = plugin;
    }

    /** Empty if StrataKits isn't installed or has no kit with that id. */
    Map<EquipmentSlot, ItemStack> forRank(String rank) {
        Map<EquipmentSlot, ItemStack> gear = new EnumMap<>(EquipmentSlot.class);
        File file = new File(plugin.getDataFolder().getParentFile(), "StrataKits/kits.yml");
        if (!file.exists()) {
            return gear;
        }
        ConfigurationSection kit = YamlConfiguration.loadConfiguration(file).getConfigurationSection("kits." + rank.toLowerCase());
        if (kit == null) {
            return gear;
        }
        for (Map<?, ?> raw : kit.getMapList("items")) {
            Material material = Material.matchMaterial(String.valueOf(raw.get("material")));
            EquipmentSlot slot = material == null ? null : slotFor(material);
            if (slot != null) {
                gear.put(slot, new ItemStack(material));
            }
        }
        return gear;
    }

    private static EquipmentSlot slotFor(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET")) {
            return EquipmentSlot.HELMET;
        }
        if (name.endsWith("_CHESTPLATE")) {
            return EquipmentSlot.CHESTPLATE;
        }
        if (name.endsWith("_LEGGINGS")) {
            return EquipmentSlot.LEGGINGS;
        }
        if (name.endsWith("_BOOTS")) {
            return EquipmentSlot.BOOTS;
        }
        return name.endsWith("_SWORD") ? EquipmentSlot.HAND : null;
    }
}
