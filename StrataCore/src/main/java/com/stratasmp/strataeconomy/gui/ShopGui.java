package com.stratasmp.strataeconomy.gui;

import com.stratasmp.strataeconomy.StrataEconomy;
import com.stratasmp.strataeconomy.shop.PriceTable;
import com.stratasmp.strataeconomy.shop.Section;
import com.stratasmp.strataeconomy.shop.SpecialOffer;
import com.stratasmp.strataeconomy.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * /shop - a category menu built from whichever {@link Section}s currently have
 * buyable items; each opens a paginated grid. Left-click buys one, shift-click
 * a full stack. Prices track the King's current multiplier.
 */
public final class ShopGui implements Listener {

    private static final int BACK_SLOT = 48;

    private static int backSlot(int page) {
        return page == 0 ? Gui.NAV_PREV : BACK_SLOT;
    }
    private static final int SPECIAL_SLOT = 4;

    private static final int SELLABLES_SLOT = 18;

    private final StrataEconomy plugin;
    private SellablesGui sellablesGui;

    public ShopGui(StrataEconomy plugin) {
        this.plugin = plugin;
    }

    public void setSellablesGui(SellablesGui sellablesGui) {
        this.sellablesGui = sellablesGui;
    }

    private PriceTable prices() {
        return (PriceTable) plugin.prices();
    }

    public void openLanding(Player player) {
        List<Section> sections = prices().activeBuySections();
        Holder holder = new Holder(null, 0);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Gui.text("Server Shop", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
        holder.inventory = inv;

        int start = 9 + Math.max(0, (9 - sections.size()) / 2);
        int[] slots = new int[sections.size()];
        for (int i = 0; i < sections.size(); i++) {
            int slot = start + i;
            slots[i] = slot;
            Section section = sections.get(i);
            ItemStack icon = Gui.button(section.icon, section.color, section.displayName,
                    "Left-click to browse", buyableCount(section) + " item(s)");
            inv.setItem(slot, Gui.glow(icon));
        }
        holder.landingSlots = slots;

        if (!plugin.specialOffers().all().isEmpty()) {
            inv.setItem(SPECIAL_SLOT, Gui.glow(Gui.button(Material.NETHER_STAR, NamedTextColor.LIGHT_PURPLE,
                    "Special Offers", "Left-click to browse", plugin.specialOffers().all().size() + " item(s)")));
        }

        if (sellablesGui != null) {
            inv.setItem(SELLABLES_SLOT, Gui.button(Material.EMERALD, NamedTextColor.GREEN,
                    "What can I sell?", "Left-click to see what /sell buys", "and what each item is worth"));
        }

        ItemStack pane = Gui.button(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, pane);
            }
        }
        inv.setItem(22, Gui.button(Material.GOLD_NUGGET, NamedTextColor.YELLOW,
                "Your balance: " + plugin.money(plugin.stratas().getBalance(player.getUniqueId()))));
        player.openInventory(inv);
    }

    public void openSpecialOffers(Player player, int page) {
        List<SpecialOffer> offers = plugin.specialOffers().all();
        int pages = Gui.totalPages(offers.size());
        page = Math.max(0, Math.min(page, pages - 1));

        Holder holder = new Holder(null, page);
        holder.special = true;
        Inventory inv = Bukkit.createInventory(holder, Gui.SIZE,
                Gui.text("Special Offers", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true)
                        .append(Component.text("  (" + (page + 1) + "/" + pages + ")", NamedTextColor.GRAY)));
        holder.inventory = inv;

        int start = page * Gui.PAGE_SLOTS;
        for (int i = start; i < Math.min(offers.size(), start + Gui.PAGE_SLOTS); i++) {
            SpecialOffer offer = offers.get(i);
            ItemStack icon = new ItemStack(offer.icon);
            var meta = icon.getItemMeta();
            meta.displayName(Msg.color(offer.name));
            List<Component> lore = new java.util.ArrayList<>();
            offer.lore.forEach(l -> lore.add(Msg.color(l)));
            lore.add(Gui.text("Price: " + plugin.money(offer.price), NamedTextColor.GOLD));
            lore.add(Gui.text("Left-click to buy", NamedTextColor.YELLOW));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(i - start, icon);
        }
        Gui.frame(inv, pages, page);
        inv.setItem(backSlot(page), Gui.button(Material.OAK_DOOR, "Back to categories"));
        player.openInventory(inv);
    }

    private int buyableCount(Section section) {
        return prices().buyableIn(section).size();
    }

    public void openCategory(Player player, Section section, int page) {
        List<Material> items = prices().buyableIn(section);
        int pages = Gui.totalPages(items.size());
        page = Math.max(0, Math.min(page, pages - 1));

        Holder holder = new Holder(section, page);
        Inventory inv = Bukkit.createInventory(holder, Gui.SIZE,
                Gui.text(section.displayName, section.color).decoration(TextDecoration.BOLD, true)
                        .append(Component.text("  (" + (page + 1) + "/" + pages + ")", NamedTextColor.GRAY)));
        holder.inventory = inv;

        int start = page * Gui.PAGE_SLOTS;
        for (int i = start; i < Math.min(items.size(), start + Gui.PAGE_SLOTS); i++) {
            Material m = items.get(i);
            int unit = plugin.prices().buyPrice(m);
            int cap = prices().dailyBuyLimit(m);
            ItemStack icon = new ItemStack(m);
            var meta = icon.getItemMeta();
            meta.displayName(Gui.text(displayName(m), section.color));
            List<Component> lore = new java.util.ArrayList<>(List.of(
                    Gui.text("Buy price: " + plugin.money(unit), NamedTextColor.GOLD),
                    Gui.text("Shift-click buys a full stack", NamedTextColor.YELLOW)));
            if (cap > 0) {
                int left = plugin.purchaseLimits().remaining(player.getUniqueId(), m, cap);
                lore.add(left > 0
                        ? Gui.text(left + "/" + cap + " left today", NamedTextColor.AQUA)
                        : Gui.text("Daily limit reached", NamedTextColor.RED));
            }
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(i - start, icon);
        }
        Gui.frame(inv, pages, page);
        inv.setItem(backSlot(page), Gui.button(Material.OAK_DOOR, "Back to categories"));
        player.openInventory(inv);
    }

    private String displayName(Material m) {
        String[] words = m.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
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

        if (holder.special) {
            handleSpecialOfferClick(player, holder, slot);
            return;
        }

        if (holder.category == null) {
            List<Section> sections = prices().activeBuySections();
            for (int i = 0; i < holder.landingSlots.length && i < sections.size(); i++) {
                if (slot == holder.landingSlots[i]) {
                    openCategory(player, sections.get(i), 0);
                    return;
                }
            }
            if (slot == SPECIAL_SLOT && !plugin.specialOffers().all().isEmpty()) {
                openSpecialOffers(player, 0);
                return;
            }
            if (slot == SELLABLES_SLOT && sellablesGui != null) {
                sellablesGui.openLanding(player);
            }
            return;
        }

        if (slot == Gui.NAV_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == backSlot(holder.page)) {
            openLanding(player);
            return;
        }
        if (slot == Gui.NAV_PREV) {
            openCategory(player, holder.category, holder.page - 1);
            return;
        }
        if (slot == Gui.NAV_NEXT) {
            openCategory(player, holder.category, holder.page + 1);
            return;
        }
        if (slot < 0 || slot >= Gui.PAGE_SLOTS) {
            return;
        }
        List<Material> items = prices().buyableIn(holder.category);
        int index = holder.page * Gui.PAGE_SLOTS + slot;
        if (index >= items.size()) {
            return;
        }
        Material m = items.get(index);
        boolean stack = event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT;
        int requested = stack ? m.getMaxStackSize() : 1;

        int cap = prices().dailyBuyLimit(m);
        int left = plugin.purchaseLimits().remaining(player.getUniqueId(), m, cap);
        if (left <= 0) {
            plugin.msg().send(player, "shop-daily-limit", Map.of("item", displayName(m)));
            return;
        }
        int amount = Math.min(requested, left);
        long cost = (long) plugin.prices().buyPrice(m) * amount;

        if (!plugin.stratas().has(player.getUniqueId(), cost)) {
            plugin.msg().send(player, "shop-cant-afford");
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            plugin.msg().send(player, "shop-inventory-full");
            return;
        }
        plugin.stratas().withdraw(player.getUniqueId(), cost);
        player.getInventory().addItem(new ItemStack(m, amount));
        plugin.purchaseLimits().record(player.getUniqueId(), m, amount);
        plugin.msg().send(player, amount < requested ? "shop-bought-limited" : "shop-bought", Map.of(
                "item", amount + "x " + m.name().toLowerCase().replace('_', ' '),
                "price", plugin.money(cost)));
        openCategory(player, holder.category, holder.page);
    }

    private void handleSpecialOfferClick(Player player, Holder holder, int slot) {
        if (slot == Gui.NAV_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == backSlot(holder.page)) {
            openLanding(player);
            return;
        }
        if (slot == Gui.NAV_PREV) {
            openSpecialOffers(player, holder.page - 1);
            return;
        }
        if (slot == Gui.NAV_NEXT) {
            openSpecialOffers(player, holder.page + 1);
            return;
        }
        if (slot < 0 || slot >= Gui.PAGE_SLOTS) {
            return;
        }
        List<SpecialOffer> offers = plugin.specialOffers().all();
        int index = holder.page * Gui.PAGE_SLOTS + slot;
        if (index >= offers.size()) {
            return;
        }
        SpecialOffer offer = offers.get(index);
        if (!plugin.stratas().has(player.getUniqueId(), offer.price)) {
            plugin.msg().send(player, "shop-cant-afford");
            return;
        }
        if (!plugin.stratas().withdraw(player.getUniqueId(), offer.price)) {
            plugin.msg().send(player, "shop-cant-afford");
            return;
        }
        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), offer.command.replace("%player%", player.getName()))) {
            // the command didn't run (missing plugin, bad command): give the money back
            plugin.stratas().deposit(player.getUniqueId(), offer.price);
            player.sendMessage(Gui.text("That offer is unavailable right now. You were not charged.", NamedTextColor.RED));
            return;
        }
        plugin.msg().send(player, "shop-bought-special", Map.of(
                "item", offer.name,
                "price", plugin.money(offer.price)));
        openSpecialOffers(player, holder.page);
    }

    static final class Holder implements InventoryHolder {
        final Section category;
        final int page;
        boolean special;
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
