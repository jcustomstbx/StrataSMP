package com.stratasmp.strataeconomy.gui;

import com.stratasmp.strataeconomy.StrataEconomy;
import com.stratasmp.strataeconomy.shop.PriceTable;
import com.stratasmp.strataeconomy.shop.Section;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** /shop sellables - a read-only price list of everything /sell will buy, grouped like the shop. */
public final class SellablesGui implements Listener {

    private static final int BACK_SLOT = 48;

    private final StrataEconomy plugin;

    public SellablesGui(StrataEconomy plugin) {
        this.plugin = plugin;
    }

    private PriceTable prices() {
        return (PriceTable) plugin.prices();
    }

    public void openLanding(Player player) {
        List<Section> sections = prices().activeSellSections();
        Holder holder = new Holder(null, 0);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Gui.text("What can I sell?", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
        holder.inventory = inv;

        ItemStack pane = Gui.button(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane);
        }
        int start = 9 + Math.max(0, (9 - sections.size()) / 2);
        int[] slots = new int[sections.size()];
        for (int i = 0; i < sections.size(); i++) {
            Section section = sections.get(i);
            slots[i] = start + i;
            inv.setItem(slots[i], Gui.glow(Gui.button(section.icon, section.color, section.displayName,
                    "Left-click to see prices", prices().sellableIn(section).size() + " item(s)")));
        }
        holder.landingSlots = slots;
        inv.setItem(22, Gui.button(Material.CHEST, NamedTextColor.YELLOW, "Sell with /sell",
                "Put items in the menu, or use /sell hand"));
        player.openInventory(inv);
    }

    public void openCategory(Player player, Section section, int page) {
        List<Material> items = prices().sellableIn(section);
        int pages = Gui.totalPages(items.size());
        page = Math.max(0, Math.min(page, pages - 1));

        Holder holder = new Holder(section, page);
        Inventory inv = Bukkit.createInventory(holder, Gui.SIZE,
                Gui.text(section.displayName, section.color).decoration(TextDecoration.BOLD, true)
                        .append(Component.text("  (" + (page + 1) + "/" + pages + ")", NamedTextColor.GRAY)));
        holder.inventory = inv;

        double tax = plugin.getConfig().getDouble("shop.sell-tax-percent", 0.0);
        int start = page * Gui.PAGE_SLOTS;
        for (int i = start; i < Math.min(items.size(), start + Gui.PAGE_SLOTS); i++) {
            Material m = items.get(i);
            int unit = plugin.prices().sellPrice(m);
            ItemStack icon = new ItemStack(m);
            var meta = icon.getItemMeta();
            meta.displayName(Gui.text(displayName(m), section.color));
            List<Component> lore = new ArrayList<>();
            lore.add(Gui.text("Sells for: " + plugin.money(unit) + " each", NamedTextColor.GOLD));
            if (tax > 0) {
                lore.add(Gui.text("Minus " + tax + "% sell tax", NamedTextColor.GRAY));
            }
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(i - start, icon);
        }
        Gui.frame(inv, pages, page);
        inv.setItem(BACK_SLOT, Gui.button(Material.OAK_DOOR, "Back to categories"));
        player.openInventory(inv);
    }

    private static String displayName(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String word : m.name().toLowerCase().split("_")) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (Gui.isRestrictedWorld(plugin, player)) {
            player.closeInventory();
            player.sendMessage(plugin.msg().plain("menu-restricted-world"));
            return;
        }
        int slot = event.getRawSlot();

        if (holder.category == null) {
            List<Section> sections = prices().activeSellSections();
            for (int i = 0; i < holder.landingSlots.length && i < sections.size(); i++) {
                if (slot == holder.landingSlots[i]) {
                    openCategory(player, sections.get(i), 0);
                    return;
                }
            }
            return;
        }
        if (slot == Gui.NAV_CLOSE) {
            player.closeInventory();
        } else if (slot == BACK_SLOT) {
            openLanding(player);
        } else if (slot == Gui.NAV_PREV && holder.page > 0) {
            openCategory(player, holder.category, holder.page - 1);
        } else if (slot == Gui.NAV_NEXT) {
            openCategory(player, holder.category, holder.page + 1);
        }
    }

    static final class Holder implements InventoryHolder {
        final Section category;
        final int page;
        int[] landingSlots = new int[0];
        Inventory inventory;

        Holder(Section category, int page) {
            this.category = category;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
