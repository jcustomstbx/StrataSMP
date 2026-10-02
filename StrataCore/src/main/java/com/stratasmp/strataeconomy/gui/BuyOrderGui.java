package com.stratasmp.strataeconomy.gui;

import com.stratasmp.strataeconomy.StrataEconomy;
import com.stratasmp.strataeconomy.api.BuyOrders;
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

import java.util.List;
import java.util.UUID;

/**
 * /ah buyorders - browse standing buy orders (left-click sells as many as you're
 * holding, up to what's left on the order), your own orders (left-click to cancel
 * and get the remaining escrow back), and pending deliveries for orders that got
 * filled while you were offline (left-click to claim into your inventory).
 */
public final class BuyOrderGui implements Listener {

    private enum View { BROWSE, MINE, DELIVERIES }

    private final StrataEconomy plugin;
    private AuctionGui auctionGui;

    public BuyOrderGui(StrataEconomy plugin) {
        this.plugin = plugin;
    }

    public void setAuctionGui(AuctionGui auctionGui) {
        this.auctionGui = auctionGui;
    }

    public void openBrowse(Player player, int page) {
        render(player, View.BROWSE, plugin.buyOrders().all(), page);
    }

    public void openMine(Player player, int page) {
        render(player, View.MINE, plugin.buyOrders().byBuyer(player.getUniqueId()), page);
    }

    public void openDeliveries(Player player, int page) {
        renderDeliveries(player, page);
    }

    private void render(Player player, View view, List<BuyOrders.Order> list, int page) {
        int pages = Gui.totalPages(list.size());
        page = Math.max(0, Math.min(page, pages - 1));

        Holder holder = new Holder(view, page);
        Inventory inv = Bukkit.createInventory(holder, Gui.SIZE,
                Gui.text(view == View.MINE ? "My Buy Orders" : "Buy Orders", NamedTextColor.DARK_AQUA)
                        .append(Component.text("  (" + (page + 1) + "/" + pages + ")", NamedTextColor.GRAY)));
        holder.inventory = inv;

        int start = page * Gui.PAGE_SLOTS;
        for (int i = start; i < Math.min(list.size(), start + Gui.PAGE_SLOTS); i++) {
            BuyOrders.Order o = list.get(i);
            inv.setItem(i - start, Gui.withLore(new ItemStack(o.material()), List.of(
                    Gui.text("Wants: " + o.amountRemaining() + "x " + o.material().name().toLowerCase().replace('_', ' '), NamedTextColor.WHITE),
                    Gui.text("Price each: " + plugin.money(o.pricePerItem()), NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                    view == View.MINE
                            ? Gui.text("Left-click to cancel (refunds remaining balance)", NamedTextColor.GRAY)
                            : Gui.text("Buyer: " + o.buyerName(), NamedTextColor.GRAY),
                    view == View.MINE ? Component.empty()
                            : Gui.text("Left-click to sell (as many as you're holding)", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true))));
        }
        Gui.frame(inv, pages, page);

        if (view == View.BROWSE) {
            inv.setItem(46, Gui.glow(Gui.button(Material.WRITABLE_BOOK, NamedTextColor.AQUA, "My buy orders", "Left-click")));
            inv.setItem(48, Gui.button(Material.PAPER, NamedTextColor.YELLOW, "Post a buy order",
                    "Hold the item you want, then run", "/ah buyorder <price> [amount]"));
            int pending = plugin.buyOrders().deliveriesFor(player.getUniqueId()).size();
            inv.setItem(50, Gui.glow(Gui.button(Material.CHEST, NamedTextColor.LIGHT_PURPLE,
                    "My deliveries" + (pending > 0 ? " (" + pending + ")" : ""), "Left-click")));
        }
        inv.setItem(52, Gui.glow(Gui.button(Material.ARROW, NamedTextColor.WHITE,
                view == View.BROWSE ? "Back to Auction House" : "Back to Buy Orders", "Left-click")));

        player.openInventory(inv);
    }

    private void renderDeliveries(Player player, int page) {
        List<BuyOrders.Delivery> list = plugin.buyOrders().deliveriesFor(player.getUniqueId());
        int pages = Gui.totalPages(list.size());
        page = Math.max(0, Math.min(page, pages - 1));

        Holder holder = new Holder(View.DELIVERIES, page);
        Inventory inv = Bukkit.createInventory(holder, Gui.SIZE,
                Gui.text("My Deliveries", NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text("  (" + (page + 1) + "/" + pages + ")", NamedTextColor.GRAY)));
        holder.inventory = inv;

        int start = page * Gui.PAGE_SLOTS;
        for (int i = start; i < Math.min(list.size(), start + Gui.PAGE_SLOTS); i++) {
            BuyOrders.Delivery d = list.get(i);
            ItemStack icon = new ItemStack(d.material(), Math.min(d.amount(), d.material().getMaxStackSize()));
            inv.setItem(i - start, Gui.withLore(icon, List.of(
                    Gui.text("Amount: " + d.amount() + "x " + d.material().name().toLowerCase().replace('_', ' '), NamedTextColor.WHITE),
                    Gui.text("A buy order of yours was filled while you", NamedTextColor.GRAY),
                    Gui.text("were offline - left-click to collect it.", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true))));
        }
        Gui.frame(inv, pages, page);
        inv.setItem(52, Gui.glow(Gui.button(Material.ARROW, NamedTextColor.WHITE, "Back to Buy Orders", "Left-click")));

        player.openInventory(inv);
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

        if (slot == Gui.NAV_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == Gui.NAV_PREV) {
            reopen(player, holder, holder.page - 1);
            return;
        }
        if (slot == Gui.NAV_NEXT) {
            reopen(player, holder, holder.page + 1);
            return;
        }
        if (slot == 52) {
            if (holder.view == View.BROWSE && auctionGui != null) {
                auctionGui.openBrowse(player, 0);
            } else {
                openBrowse(player, 0);
            }
            return;
        }
        if (holder.view == View.BROWSE && slot == 46) {
            openMine(player, 0);
            return;
        }
        if (holder.view == View.BROWSE && slot == 48) {
            player.sendMessage(Gui.text("Hold the item you want, then run /ah buyorder <price> [amount].", NamedTextColor.YELLOW));
            return;
        }
        if (holder.view == View.BROWSE && slot == 50) {
            openDeliveries(player, 0);
            return;
        }
        if (slot < 0 || slot >= Gui.PAGE_SLOTS) {
            return;
        }

        if (holder.view == View.DELIVERIES) {
            List<BuyOrders.Delivery> list = plugin.buyOrders().deliveriesFor(player.getUniqueId());
            int index = holder.page * Gui.PAGE_SLOTS + slot;
            if (index >= list.size()) {
                return;
            }
            String err = plugin.buyOrders().claim(player, list.get(index).id());
            if (err != null) {
                player.sendMessage(Gui.text(err, NamedTextColor.RED));
            } else {
                player.sendMessage(plugin.msg().plain("bo-claimed"));
            }
            openDeliveries(player, holder.page);
            return;
        }

        List<BuyOrders.Order> list = holder.view == View.MINE
                ? plugin.buyOrders().byBuyer(player.getUniqueId())
                : plugin.buyOrders().all();
        int index = holder.page * Gui.PAGE_SLOTS + slot;
        if (index >= list.size()) {
            return;
        }
        UUID id = list.get(index).id();

        if (holder.view == View.MINE) {
            String err = plugin.buyOrders().cancel(player, id);
            player.sendMessage(err != null ? Gui.text(err, NamedTextColor.RED) : plugin.msg().plain("bo-cancelled"));
            openMine(player, holder.page);
            return;
        }

        // the store itself caps this to however much the seller actually has, so asking for
        // the whole remaining order just means "sell as much toward it as you can"
        String err = plugin.buyOrders().fulfill(player, id, list.get(index).amountRemaining());
        if (err != null) {
            player.sendMessage(Gui.text(err, NamedTextColor.RED));
        }
        openBrowse(player, holder.page);
    }

    private void reopen(Player p, Holder h, int page) {
        switch (h.view) {
            case MINE -> openMine(p, page);
            case DELIVERIES -> openDeliveries(p, page);
            default -> openBrowse(p, page);
        }
    }

    static final class Holder implements InventoryHolder {
        final View view;
        final int page;
        Inventory inventory;

        Holder(View view, int page) {
            this.view = view;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
