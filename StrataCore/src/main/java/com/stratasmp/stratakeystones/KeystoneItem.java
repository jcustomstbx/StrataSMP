package com.stratasmp.stratakeystones;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class KeystoneItem {
    private static final NamespacedKey LEVEL = new NamespacedKey("stratasmp", "keystone_level");

    private KeystoneItem() {}

    public static ItemStack create(int level) {
        ItemStack item = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Keystone", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text(" Lv. " + level, NamedTextColor.GOLD))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Right-click to start a run.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Clear 3 waves to rank it up.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(LEVEL, PersistentDataType.INTEGER, level);
        item.setItemMeta(meta);
        return item;
    }

    /** Returns the keystone level, or 0 if the stack is not a keystone. */
    public static int level(ItemStack item) {
        if (item == null || item.getType() != Material.ECHO_SHARD || !item.hasItemMeta()) return 0;
        Integer level = item.getItemMeta().getPersistentDataContainer().get(LEVEL, PersistentDataType.INTEGER);
        return level == null ? 0 : level;
    }
}
