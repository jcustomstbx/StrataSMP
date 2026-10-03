package com.stratasmp.strataeconomy.gui;

import com.stratasmp.strataeconomy.StrataEconomy;
import com.stratasmp.strataeconomy.api.Auctions;
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
import java.util.UUID;

/**
 * /ah - browse everyone's listings (left-click to buy), or your own (left-click
 * to cancel). Listing an item is still {@code /ah sell <price>}.
 */
public final class AuctionGui implements Listener {

    private final StrataEconomy plugin;
    private BuyOrderGui buyOrderGui;

    public AuctionGui(StrataEconomy plugin) {
        this.plugin = plugin;
    }

    public void setBuyOrderGui(BuyOrderGui buyOrderGui) {
        this.buyOrderGui = buyOrderGui;
    }

    public void openBrowse(Player player, int page) {
        render(player, plugin.auctions().all(), page, false);
    }

    public void openMine(Player player, int page) {
        render(player, plugin.auctions().bySeller(player.getUniqueId()), page, true);
    }

    private void render(Player player, List<Auctions.Listing> listings, int page, boolean mine) {
        int pages = Gui.totalPages(listings.size());
        page = Math.max(0, Math.min(page, pages - 1));

        Holder holder = new Holder(mine, page, List.copyOf(listings));
        Inventory inv = Bukkit.createInventory(holder, Gui.SIZE,
                Gui.text(mine ? "Your Listings" : "Auction House", NamedTextColor.DARK_AQUA)
                        .append(Component.text("  (" + (page + 1) + "/" + pages + ")", NamedTextColor.GRAY)));
        holder.inventory = inv;

        int start = page * Gui.PAGE_SLOTS;
        for (int i = start; i < Math.min(listings.size(), start + Gui.PAGE_SLOTS); i++) {
            Auctions.Listing l = listings.get(i);
            inv.setItem(i - start, Gui.withLore(l.item(), List.of(
                    Gui.text("Price: " + plugin.money(l.price()), NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                    Gui.text(mine ? "Left-click to cancel" : "Seller: " + l.sellerName(), NamedTextColor.GRAY),
                    mine ? Component.empty() : Gui.text("Left-click to buy", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true))));
        }
        Gui.frame(inv, pages, page);
        if (!mine) {
            inv.setItem(46, Gui.glow(Gui.button(Material.EMERALD, NamedTextColor.GREEN, "Buy orders", "Left-click")));
            inv.setItem(48, Gui.glow(Gui.button(Material.WRITABLE_BOOK, NamedTextColor.AQUA, "My listings", "Left-click")));
            inv.setItem(50, Gui.glow(Gui.button(Material.SUNFLOWER, NamedTextColor.YELLOW,
                    "Sell an item", "Hold it and run /ah sell <price>")));
        }
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
            open(player, holder, holder.page - 1);
            return;
        }
        if (slot == Gui.NAV_NEXT) {
            open(player, holder, holder.page + 1);
            return;
        }
        if (!holder.mine && slot == 46) {
            if (buyOrderGui != null) {
                buyOrderGui.openBrowse(player, 0);
            }
            return;
        }
        if (!holder.mine && slot == 48) {
            openMine(player, 0);
            return;
        }
        if (slot < 0 || slot >= Gui.PAGE_SLOTS) {
            return;
        }

        // the listings the player was actually shown, so a new listing can't shift what a slot means
        List<Auctions.Listing> list = holder.shown;
        int index = holder.page * Gui.PAGE_SLOTS + slot;
        if (index >= list.size()) {
            return;
        }
        UUID id = list.get(index).id();

        if (holder.mine || event.getClick() == ClickType.RIGHT && list.get(index).seller().equals(player.getUniqueId())) {
            String err = plugin.auctions().cancel(player, id);
            player.sendMessage(err != null ? Gui.text(err, NamedTextColor.RED) : plugin.msg().plain("ah-cancelled"));
            openMine(player, holder.page);
            return;
        }
        Auctions.Listing chosen = list.get(index);
        long confirmOver = plugin.getConfig().getLong("auction.confirm-over", 100000L);
        if (chosen.price() >= confirmOver && !id.equals(holder.confirming)) {
            holder.confirming = id;
            player.sendMessage(Gui.text("That costs " + plugin.money(chosen.price()) + ". Click it again to confirm.", NamedTextColor.YELLOW));
            return;
        }
        String err = plugin.auctions().buy(player, id);
        if (err != null) {
            player.sendMessage(Gui.text(err, NamedTextColor.RED));
        } else {
            player.sendMessage(plugin.msg().get("ah-bought", java.util.Map.of(
                    "item", list.get(index).item().getType().name().toLowerCase().replace('_', ' '),
                    "price", plugin.money(list.get(index).price()))));
        }
        openBrowse(player, holder.page);
    }

    private void open(Player p, Holder h, int page) {
        if (h.mine) {
            openMine(p, page);
        } else {
            openBrowse(p, page);
        }
    }

    static final class Holder implements InventoryHolder {
        final boolean mine;
        final int page;
        final List<Auctions.Listing> shown;
        UUID confirming;
        Inventory inventory;

        Holder(boolean mine, int page, List<Auctions.Listing> shown) {
            this.mine = mine;
            this.page = page;
            this.shown = shown;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
