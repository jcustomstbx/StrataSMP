package com.stratasmp.stratacratevault;

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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /cratevault - a paged list of everything a player's crate prizes have put in their vault; click to claim. */
final class VaultGui implements Listener {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_CLAIM_ALL = 47;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_INFO = 51;
    private static final int SLOT_NEXT = 53;

    private final StrataCrateVault plugin;
    private final VaultStore store;

    VaultGui(StrataCrateVault plugin, VaultStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    private static Component line(String text, NamedTextColor colour) {
        return Component.text(text, colour).decoration(TextDecoration.ITALIC, false);
    }

    static String prettyName(Material material) {
        StringBuilder sb = new StringBuilder();
        for (String word : material.name().toLowerCase().split("_")) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    private ItemStack button(Material material, NamedTextColor colour, String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(line(name, colour).decoration(TextDecoration.BOLD, true));
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack pane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }

    void open(Player player, int page) {
        if (!plugin.isVaultWorld(player)) {
            player.sendMessage(Component.text("Your Crate Vault can only be opened on the SMP.", NamedTextColor.RED));
            return;
        }
        List<VaultEntry> entries = store.entries(player.getUniqueId());
        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, pages - 1));

        Holder holder = new Holder(page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Crate Vault", NamedTextColor.DARK_PURPLE)
                        .append(Component.text("  (" + (page + 1) + "/" + pages + ")", NamedTextColor.GRAY)));
        holder.inventory = inv;

        int start = page * PAGE_SIZE;
        for (int i = start; i < Math.min(entries.size(), start + PAGE_SIZE); i++) {
            VaultEntry entry = entries.get(i);
            ItemStack icon = entry.toItem(Math.min(entry.amount, entry.material.getMaxStackSize()));
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>();
            lore.add(line("Stored: " + entry.amount, NamedTextColor.GRAY));
            lore.add(line("Click to claim", NamedTextColor.YELLOW));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(i - start, icon);
        }
        for (int i = PAGE_SIZE; i < 54; i++) {
            inv.setItem(i, pane());
        }
        if (page > 0) {
            inv.setItem(SLOT_PREV, button(Material.ARROW, NamedTextColor.WHITE, "Previous page", List.of()));
        }
        if (page < pages - 1) {
            inv.setItem(SLOT_NEXT, button(Material.ARROW, NamedTextColor.WHITE, "Next page", List.of()));
        }
        inv.setItem(SLOT_CLAIM_ALL, button(Material.CHEST, NamedTextColor.GREEN, "Claim everything",
                List.of(line("Moves as much as fits into your inventory", NamedTextColor.GRAY))));
        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, NamedTextColor.RED, "Close", List.of()));
        inv.setItem(SLOT_INFO, button(Material.ENDER_CHEST, NamedTextColor.LIGHT_PURPLE, "Crate Vault",
                List.of(line("Prizes from crates land here.", NamedTextColor.GRAY),
                        line(store.totalItems(player.getUniqueId()) + " item(s) stored", NamedTextColor.GRAY))));

        player.openInventory(inv);
    }

    /** Moves as much of the entry as fits; returns how many were actually handed over. */
    private int claim(Player player, VaultEntry entry) {
        int max = entry.material.getMaxStackSize();
        int remaining = entry.amount;
        int claimed = 0;
        while (remaining > 0) {
            int chunk = Math.min(max, remaining);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(entry.toItem(chunk));
            if (!leftover.isEmpty()) {
                int notPlaced = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                claimed += chunk - notPlaced;
                remaining -= chunk - notPlaced;
                break;
            }
            claimed += chunk;
            remaining -= chunk;
        }
        entry.amount = remaining;
        return claimed;
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
        if (!plugin.isVaultWorld(player)) {
            player.closeInventory();
            player.sendMessage(Component.text("Your Crate Vault can only be used on the SMP.", NamedTextColor.RED));
            return;
        }
        UUID id = player.getUniqueId();
        List<VaultEntry> entries = store.entries(id);
        int slot = event.getRawSlot();

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_PREV && holder.page > 0) {
            open(player, holder.page - 1);
            return;
        }
        if (slot == SLOT_NEXT) {
            open(player, holder.page + 1);
            return;
        }
        if (slot == SLOT_CLAIM_ALL) {
            int total = 0;
            for (VaultEntry entry : new ArrayList<>(entries)) {
                total += claim(player, entry);
                if (entry.amount > 0) {
                    break;
                }
            }
            entries.removeIf(e -> e.amount <= 0);
            store.save(id);
            player.sendMessage(total == 0
                    ? Component.text("Nothing claimed - is your inventory full?", NamedTextColor.RED)
                    : Component.text("Claimed " + total + " item(s) from your Crate Vault.", NamedTextColor.GREEN));
            open(player, holder.page);
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) {
            return;
        }
        int index = holder.page * PAGE_SIZE + slot;
        ItemStack shown = event.getCurrentItem();
        if (index >= entries.size() || shown == null || shown.getType() != entries.get(index).material) {
            open(player, holder.page);
            return;
        }
        VaultEntry entry = entries.get(index);
        int claimed = claim(player, entry);
        if (entry.amount <= 0) {
            entries.remove(index);
        }
        store.save(id);
        if (claimed == 0) {
            player.sendMessage(Component.text("Your inventory is full.", NamedTextColor.RED));
        } else {
            player.sendMessage(Component.text("Claimed " + claimed + "x " + prettyName(entry.material) + ".", NamedTextColor.GREEN));
        }
        open(player, holder.page);
    }

    static final class Holder implements InventoryHolder {
        private final int page;
        private Inventory inventory;

        Holder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
