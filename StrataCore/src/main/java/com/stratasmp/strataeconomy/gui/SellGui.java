package com.stratasmp.strataeconomy.gui;

import com.stratasmp.strataeconomy.StrataEconomy;
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
import java.util.Map;

/**
 * /sell - a live view of everything in your inventory that has a sell price.
 * Click any item to sell that stack; the button at the bottom sells the lot.
 * Nothing is dragged into the menu, so there's no dupe surface.
 */
public final class SellGui implements Listener {

    private static final int SELL_ALL_SLOT = Gui.NAV_CLOSE + 3;

    private final StrataEconomy plugin;

    public SellGui(StrataEconomy plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Preview p = preview(player);
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, Gui.SIZE,
                Gui.text("Sell to Server", NamedTextColor.DARK_GREEN));
        holder.inventory = inv;

        for (int i = 0; i < p.sellables.size() && i < Gui.PAGE_SLOTS; i++) {
            ItemStack it = p.sellables.get(i);
            int unit = plugin.prices().sellPrice(it.getType());
            inv.setItem(i, Gui.withLore(it, List.of(
                    Gui.text(plugin.money(unit) + " each", NamedTextColor.YELLOW),
                    Gui.text("Click to sell this stack (" + plugin.money((long) unit * it.getAmount()) + ")",
                            NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true))));
        }

        ItemStack pane = Gui.button(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = Gui.PAGE_SLOTS; i < Gui.SIZE; i++) {
            inv.setItem(i, pane);
        }
        inv.setItem(Gui.NAV_CLOSE, Gui.button(Material.BARRIER, "Close"));
        ItemStack sellAll = Gui.button(
                p.total > 0 ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK,
                p.total > 0 ? NamedTextColor.GREEN : NamedTextColor.RED,
                p.total > 0 ? "Sell everything (" + plugin.money(p.total) + ")" : "Nothing to sell",
                "Sells all " + p.count + " item(s) shown above");
        inv.setItem(SELL_ALL_SLOT, p.total > 0 ? Gui.glow(sellAll) : sellAll);
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
        if (!(event.getInventory().getHolder() instanceof Holder)) {
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
        if (slot == SELL_ALL_SLOT) {
            sellAll(player);
            return;
        }
        if (slot < 0 || slot >= Gui.PAGE_SLOTS) {
            return;
        }
        Preview p = preview(player);
        if (slot >= p.slots.size()) {
            return;
        }
        int invSlot = p.slots.get(slot);
        ItemStack it = player.getInventory().getItem(invSlot);
        if (!sellable(it)) {
            open(player);
            return;
        }
        int count = it.getAmount();
        long payout = afterTax((long) plugin.prices().sellPrice(it.getType()) * count);
        player.getInventory().setItem(invSlot, null);
        plugin.stratas().deposit(player.getUniqueId(), payout);
        plugin.saleLog().record(player, Map.of(it.getType(), count), payout);
        plugin.msg().send(player, "sell-done", Map.of(
                "count", String.valueOf(count), "amount", plugin.money(payout)));
        open(player);
    }

    public void sellAll(Player player) {
        Preview p = preview(player);
        if (p.total <= 0) {
            return;
        }
        for (int invSlot : p.slots) {
            player.getInventory().setItem(invSlot, null);
        }
        long payout = afterTax(p.total);
        plugin.stratas().deposit(player.getUniqueId(), payout);
        Map<Material, Integer> sold = new java.util.EnumMap<>(Material.class);
        p.sellables.forEach(stack -> sold.merge(stack.getType(), stack.getAmount(), Integer::sum));
        plugin.saleLog().record(player, sold, payout);
        plugin.msg().send(player, "sell-done", Map.of(
                "count", String.valueOf(p.count), "amount", plugin.money(payout)));
        player.closeInventory();
    }

    private long afterTax(long amount) {
        double tax = plugin.getConfig().getDouble("shop.sell-tax-percent", 0.0);
        return tax > 0 ? amount - (long) Math.floor(amount * tax / 100.0) : amount;
    }

    private boolean sellable(ItemStack it) {
        if (it == null || it.getType().isAir()) {
            return false;
        }
        if (it.hasItemMeta() && it.getItemMeta().hasCustomModelData()) {
            return false;
        }
        return plugin.prices().sellPrice(it.getType()) > 0;
    }

    private Preview preview(Player player) {
        List<ItemStack> sellables = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        long total = 0;
        int count = 0;
        // /sellall deliberately leaves equipped armour and the offhand alone.
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (!sellable(it)) {
                continue;
            }
            sellables.add(it.clone());
            slots.add(i);
            total += (long) plugin.prices().sellPrice(it.getType()) * it.getAmount();
            count += it.getAmount();
        }
        return new Preview(sellables, slots, total, count);
    }

    private record Preview(List<ItemStack> sellables, List<Integer> slots, long total, int count) {
    }

    static final class Holder implements InventoryHolder {
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
