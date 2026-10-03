package com.stratasmp.strataweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * /stratacharm - the full skin catalog. Spending StrataCharm on a locked skin (after a confirm step)
 * grants the permanent strataskins.&lt;key&gt; node that /skins already checks for.
 */
public final class StrataCharmGui implements Listener, CommandExecutor {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_MY_SKINS = 47;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_STORE = 50;
    private static final int SLOT_BALANCE = 51;
    private static final int SLOT_NEXT = 53;
    private static final int SLOT_WEAPONS = 46;
    private static final int SLOT_ARMOUR = 48;
    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_CONFIRM_ICON = 13;
    private static final int SLOT_CANCEL = 15;

    private final StrataWeapons plugin;
    private final WeaponCatalog catalog;
    private final StrataCharmService charms;
    private final Set<UUID> redeeming = new HashSet<>();
    private SkinsGui skinsGui;

    public StrataCharmGui(StrataWeapons plugin, WeaponCatalog catalog, StrataCharmService charms) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.charms = charms;
    }

    public void setSkinsGui(SkinsGui skinsGui) {
        this.skinsGui = skinsGui;
    }

    public int balanceOf(Player player) {
        return charms.getBalance(player.getUniqueId());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        openCatalog(player, 0, false);
        return true;
    }

    private List<String> sortedKeys() {
        List<String> keys = new ArrayList<>(catalog.keys());
        keys.sort(Comparator.<String>comparingInt(catalog::menuOrder)
                .thenComparing(k -> catalog.displayNameOf(k).toLowerCase()));
        return keys;
    }

    private boolean owns(Player player, String key) {
        return player.hasPermission("strataskins.*") || player.hasPermission("strataskins." + key);
    }

    private static Component line(String text, NamedTextColor colour) {
        return Component.text(text, colour).decoration(TextDecoration.ITALIC, false);
    }

    private static String charmWord(int amount) {
        return amount == 1 ? "StrataCharm" : "StrataCharms";
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

    private ItemStack catalogIcon(Player player, String key) {
        ItemStack icon = catalog.menuIcon(key, true);
        ItemMeta meta = icon.getItemMeta();
        if (owns(player, key)) {
            meta.lore(List.of(line("Unlocked", NamedTextColor.GREEN)));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            int cost = catalog.charmCostOf(key);
            meta.lore(List.of(
                    line("Cost: " + cost + " " + charmWord(cost), NamedTextColor.GOLD),
                    line("Click to unlock", NamedTextColor.YELLOW)));
        }
        icon.setItemMeta(meta);
        return icon;
    }

    public void openCatalog(Player player, int page) {
        openCatalog(player, page, false);
    }

    public void openCatalog(Player player, int page, boolean armour) {
        List<String> keys = sortedKeys().stream()
                .filter(key -> catalog.isArmorSkin(key) == armour).toList();
        int pages = Math.max(1, (int) Math.ceil(keys.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, pages - 1));

        CatalogHolder holder = new CatalogHolder(page, armour);
        String category = armour ? "Armour" : "Weapons";
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Skin Catalog - " + category, NamedTextColor.DARK_PURPLE)
                        .append(Component.text("  (" + (page + 1) + "/" + pages + ")", NamedTextColor.GRAY)));
        holder.inventory = inv;

        int start = page * PAGE_SIZE;
        for (int i = start; i < Math.min(keys.size(), start + PAGE_SIZE); i++) {
            inv.setItem(i - start, catalogIcon(player, keys.get(i)));
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
        inv.setItem(SLOT_WEAPONS, categoryButton(Material.IRON_SWORD, "Weapons", !armour));
        inv.setItem(SLOT_ARMOUR, categoryButton(Material.DIAMOND_CHESTPLATE, "Armour", armour));
        inv.setItem(SLOT_MY_SKINS, button(Material.DIAMOND_SWORD, NamedTextColor.AQUA, "Your skins",
                List.of(line("Apply a skin you own", NamedTextColor.GRAY))));
        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, NamedTextColor.RED, "Close", List.of()));
        inv.setItem(SLOT_STORE, button(Material.EMERALD, NamedTextColor.GREEN, "Store",
                List.of(line("Click for a link to buy StrataCharm", NamedTextColor.GRAY))));
        int balance = balanceOf(player);
        inv.setItem(SLOT_BALANCE, button(Material.NETHER_STAR, NamedTextColor.LIGHT_PURPLE,
                "StrataCharm: " + balance,
                List.of(line("Get more at stratasmp.com/store", NamedTextColor.GRAY))));

        player.openInventory(inv);
    }

    private ItemStack categoryButton(Material material, String label, boolean selected) {
        return button(material, selected ? NamedTextColor.AQUA : NamedTextColor.GRAY,
                (selected ? "✓ " : "") + label,
                List.of(line(selected ? "Showing this category" : "Click to view this category",
                        selected ? NamedTextColor.AQUA : NamedTextColor.GRAY)));
    }

    private void openConfirm(Player player, String key, int returnPage, boolean armour) {
        int cost = catalog.charmCostOf(key);
        ConfirmHolder holder = new ConfirmHolder(key, returnPage, armour);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Confirm Unlock", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true));
        holder.inventory = inv;
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane());
        }

        ItemStack icon = catalog.menuIcon(key, true);
        ItemMeta meta = icon.getItemMeta();
        meta.lore(List.of(
                line("Permanent unlock - this can't be undone.", NamedTextColor.GRAY),
                line("Costs " + cost + " " + charmWord(cost), NamedTextColor.GOLD)));
        icon.setItemMeta(meta);
        inv.setItem(SLOT_CONFIRM_ICON, icon);

        inv.setItem(SLOT_CONFIRM, button(Material.LIME_CONCRETE, NamedTextColor.GREEN, "Confirm",
                List.of(line("Spend " + cost + " " + charmWord(cost), NamedTextColor.GRAY),
                        line("You have " + balanceOf(player), NamedTextColor.GRAY))));
        inv.setItem(SLOT_CANCEL, button(Material.RED_CONCRETE, NamedTextColor.RED, "Cancel", List.of()));

        player.openInventory(inv);
    }

    private void redeem(Player player, String key, int returnPage, boolean armour) {
        UUID id = player.getUniqueId();
        if (redeeming.contains(id)) {
            return;
        }
        if (owns(player, key)) {
            player.sendMessage(Component.text("You already own that skin.", NamedTextColor.YELLOW));
            openCatalog(player, returnPage, armour);
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            player.sendMessage(Component.text("Skin unlocking is unavailable right now - try again later.", NamedTextColor.RED));
            player.closeInventory();
            return;
        }
        int cost = catalog.charmCostOf(key);
        if (!charms.withdraw(id, cost)) {
            player.sendMessage(Component.text("You need " + cost + " " + charmWord(cost) + " - you have " + balanceOf(player)
                    + ". Get more at stratasmp.com/store", NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        redeeming.add(id);
        java.util.concurrent.CompletableFuture<?> grant;
        try {
            grant = LuckPermsUnlocker.grant(id, key);
        } catch (RuntimeException e) {
            // LuckPerms present but not usable: undo the charge and free the player's clicks
            charms.deposit(id, cost);
            redeeming.remove(id);
            plugin.getLogger().warning("Skin unlock could not start for " + player.getName() + " (" + key + "), refunded: " + e);
            player.sendMessage(Component.text("Skin unlocking is unavailable right now - you were not charged.", NamedTextColor.RED));
            player.closeInventory();
            return;
        }
        grant.whenComplete((ignored, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    redeeming.remove(id);
                    if (error != null) {
                        charms.deposit(id, cost);
                        plugin.getLogger().warning("Skin unlock failed for " + player.getName() + " (" + key + "), "
                                + cost + " charm(s) refunded: " + error);
                        if (player.isOnline()) {
                            player.sendMessage(Component.text("Something went wrong unlocking that skin - your charm was refunded.",
                                    NamedTextColor.RED));
                            player.closeInventory();
                        }
                        return;
                    }
                    plugin.getLogger().info(player.getName() + " spent " + cost + " StrataCharm(s) to unlock " + key + ".");
                    if (player.isOnline()) {
                        player.sendMessage(Component.text("Unlocked ", NamedTextColor.GREEN)
                                .append(Component.text(catalog.displayNameOf(key), NamedTextColor.GOLD))
                                .append(Component.text("! Use /skins to apply it.", NamedTextColor.GREEN)));
                        openCatalog(player, returnPage, armour);
                    }
                }));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof CatalogHolder || holder instanceof ConfirmHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof CatalogHolder) && !(holder instanceof ConfirmHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (redeeming.contains(player.getUniqueId())) {
            return;
        }
        int slot = event.getRawSlot();
        if (holder instanceof ConfirmHolder confirm) {
            if (slot == SLOT_CONFIRM) {
                redeem(player, confirm.key, confirm.returnPage, confirm.armour);
            } else if (slot == SLOT_CANCEL) {
                openCatalog(player, confirm.returnPage, confirm.armour);
            }
            return;
        }

        CatalogHolder catalogHolder = (CatalogHolder) holder;
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_PREV && catalogHolder.page > 0) {
            openCatalog(player, catalogHolder.page - 1, catalogHolder.armour);
            return;
        }
        if (slot == SLOT_NEXT) {
            openCatalog(player, catalogHolder.page + 1, catalogHolder.armour);
            return;
        }
        if (slot == SLOT_WEAPONS) {
            openCatalog(player, 0, false);
            return;
        }
        if (slot == SLOT_ARMOUR) {
            openCatalog(player, 0, true);
            return;
        }
        if (slot == SLOT_STORE) {
            StoreLink.send(plugin, player);
            return;
        }
        if (slot == SLOT_MY_SKINS && skinsGui != null) {
            skinsGui.open(player, 0, catalogHolder.armour);
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) {
            return;
        }
        List<String> keys = sortedKeys().stream()
                .filter(key -> catalog.isArmorSkin(key) == catalogHolder.armour).toList();
        int index = catalogHolder.page * PAGE_SIZE + slot;
        if (index >= keys.size()) {
            return;
        }
        String key = keys.get(index);
        if (owns(player, key)) {
            player.sendMessage(Component.text("You already own that skin.", NamedTextColor.YELLOW));
            return;
        }
        int cost = catalog.charmCostOf(key);
        if (balanceOf(player) < cost) {
            player.sendMessage(Component.text("You need " + cost + " " + charmWord(cost) + " for that - you have "
                    + balanceOf(player) + ". Get more at stratasmp.com/store", NamedTextColor.RED));
            return;
        }
        openConfirm(player, key, catalogHolder.page, catalogHolder.armour);
    }

    static final class CatalogHolder implements InventoryHolder {
        private final int page;
        private final boolean armour;
        private Inventory inventory;

        CatalogHolder(int page, boolean armour) {
            this.page = page;
            this.armour = armour;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    static final class ConfirmHolder implements InventoryHolder {
        private final String key;
        private final int returnPage;
        private final boolean armour;
        private Inventory inventory;

        ConfirmHolder(String key, int returnPage, boolean armour) {
            this.key = key;
            this.returnPage = returnPage;
            this.armour = armour;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
