package com.stratasmp.strataeconomy.gui;

import com.stratasmp.strataeconomy.StrataEconomy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Small shared helpers for the StrataEconomy menus. */
public final class Gui {

    public static final int SIZE = 54;
    public static final int NAV_PREV = 45;
    public static final int NAV_CLOSE = 49;
    public static final int NAV_NEXT = 53;
    public static final int PAGE_SLOTS = 45;

    private Gui() {
    }

    public static ItemStack button(Material material, String name, String... lore) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(text(name, NamedTextColor.WHITE));
        if (lore.length > 0) {
            meta.lore(java.util.Arrays.stream(lore).map(l -> text(l, NamedTextColor.GRAY)).toList());
        }
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack button(Material material, NamedTextColor nameColour, String name, String... lore) {
        ItemStack it = button(material, name, lore);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(text(name, nameColour).decoration(TextDecoration.BOLD, true));
        it.setItemMeta(meta);
        return it;
    }

    /** Enchant-glint look with no tooltip enchant line - used to make menu buttons stand out. */
    public static ItemStack glow(ItemStack item) {
        ItemStack it = item.clone();
        ItemMeta meta = it.getItemMeta();
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(meta);
        return it;
    }

    public static void frame(Inventory inv, int pages, int page) {
        ItemStack pane = button(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = PAGE_SLOTS; i < SIZE; i++) {
            inv.setItem(i, pane);
        }
        if (page > 0) {
            inv.setItem(NAV_PREV, button(Material.ARROW, "Previous page"));
        }
        if (page < pages - 1) {
            inv.setItem(NAV_NEXT, button(Material.ARROW, "Next page"));
        }
        inv.setItem(NAV_CLOSE, button(Material.BARRIER, "Close"));
    }

    public static ItemStack withLore(ItemStack base, List<Component> lore) {
        ItemStack it = base.clone();
        ItemMeta meta = it.getItemMeta();
        List<Component> existing = meta.lore();
        if (existing == null) {
            existing = new java.util.ArrayList<>();
        } else {
            existing = new java.util.ArrayList<>(existing);
        }
        existing.addAll(lore);
        meta.lore(existing);
        it.setItemMeta(meta);
        return it;
    }

    public static Component text(String s, NamedTextColor colour) {
        return Component.text(s, colour).decoration(TextDecoration.ITALIC, false);
    }

    public static int totalPages(int itemCount) {
        return Math.max(1, (int) Math.ceil(itemCount / (double) PAGE_SLOTS));
    }

    /**
     * A menu opened in one world (e.g. the SMP) stays open if the player then walks or is
     * teleported into a restricted world (e.g. the hub) - clicks aren't gated by the command
     * block that stopped them opening it there in the first place, so this re-checks on every click.
     */
    public static boolean isRestrictedWorld(StrataEconomy plugin, Player player) {
        return plugin.getConfig().getStringList("restricted-worlds").stream()
                .anyMatch(w -> w.equalsIgnoreCase(player.getWorld().getName()));
    }
}
