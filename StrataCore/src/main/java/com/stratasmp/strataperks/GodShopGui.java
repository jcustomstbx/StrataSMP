package com.stratasmp.strataperks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/** /strataperkshop - a single grid of god-tier items, one click to buy. */
public final class GodShopGui implements Listener, CommandExecutor {

    private static final int SIZE = 36;

    private final StrataPerks plugin;

    public GodShopGui(StrataPerks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "players-only");
            return true;
        }
        open(player);
        return true;
    }

    public void open(Player player) {
        List<GodItem> items = plugin.catalog().all();
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                Component.text("StrataPerk Shop", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true));
        holder.inventory = inv;

        for (int i = 0; i < items.size() && i < SIZE - 9; i++) {
            GodItem item = items.get(i);
            ItemStack icon = plugin.catalog().build(item);
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new java.util.ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
            lore.add(Component.text("Price: " + item.price + " " + plugin.currencyName(item.price), NamedTextColor.GOLD));
            lore.add(Component.text("Left-click to buy", NamedTextColor.YELLOW));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(i, icon);
        }

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        paneMeta.displayName(Component.text(" "));
        pane.setItemMeta(paneMeta);
        for (int i = SIZE - 9; i < SIZE; i++) {
            inv.setItem(i, pane);
        }

        long balanceAmount = plugin.strataperks().getBalance(player.getUniqueId());
        ItemStack balance = new ItemStack(Material.NETHER_STAR);
        ItemMeta balMeta = balance.getItemMeta();
        balMeta.displayName(Component.text(
                "Your balance: " + balanceAmount + " " + plugin.currencyName(balanceAmount),
                NamedTextColor.YELLOW));
        balance.setItemMeta(balMeta);
        inv.setItem(SIZE - 5, balance);

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
        int slot = event.getRawSlot();
        List<GodItem> items = plugin.catalog().all();
        if (slot < 0 || slot >= items.size()) {
            return;
        }
        GodItem item = items.get(slot);

        if (!plugin.strataperks().withdraw(player.getUniqueId(), item.price)) {
            plugin.msg().send(player, "shop-cant-afford");
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            plugin.strataperks().deposit(player.getUniqueId(), item.price);
            plugin.msg().send(player, "shop-inventory-full");
            return;
        }
        player.getInventory().addItem(plugin.catalog().build(item));
        plugin.msg().send(player, "shop-bought", Map.of(
                "item", item.name.replaceAll("&.", ""),
                "price", String.valueOf(item.price),
                "currency", plugin.currencyName(item.price)));
        open(player);
    }

    static final class Holder implements InventoryHolder {
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
