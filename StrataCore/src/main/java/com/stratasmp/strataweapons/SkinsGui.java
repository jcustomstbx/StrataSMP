package com.stratasmp.strataweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * /skins - browse the weapon skins you own and stamp one onto the melee
 * weapon in your hand. Ownership is a LuckPerms node: strataskins.&lt;key&gt;
 * (strataskins.* unlocks everything, for staff).
 */
public final class SkinsGui implements Listener {

    private static final int PAGE_SIZE = 45;

    private static final int SLOT_CHARMS = 47;
    private static final int SLOT_STORE = 51;
    private static final int SLOT_WEAPONS = 46;
    private static final int SLOT_ARMOUR = 48;

    private final StrataWeapons plugin;
    private final WeaponCatalog catalog;
    private StrataCharmGui charmGui;

    public SkinsGui(StrataWeapons plugin, WeaponCatalog catalog) {
        this.plugin = plugin;
        this.catalog = catalog;
    }

    public void setCharmGui(StrataCharmGui charmGui) {
        this.charmGui = charmGui;
    }

    public void open(Player player, int page) {
        open(player, page, false);
    }

    public void open(Player player, int page, boolean armour) {
        List<String> allOwned = ownedSkins(player);
        if (allOwned.isEmpty()) {
            if (charmGui != null && charmGui.balanceOf(player) > 0) {
                charmGui.openCatalog(player, 0, armour);
                return;
            }
            player.sendMessage(Component.text("You don't own any skins yet - grab StrataCharm at stratasmp.com/store",
                    NamedTextColor.YELLOW));
            return;
        }
        List<String> owned = allOwned.stream().filter(key -> catalog.isArmorSkin(key) == armour).toList();
        int pages = Math.max(1, (int) Math.ceil(owned.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, pages - 1));

        Holder holder = new Holder(page, armour);
        String category = armour ? "Armour" : "Weapons";
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Skins - " + category, NamedTextColor.DARK_PURPLE)
                        .append(Component.text("  (" + (page + 1) + "/" + pages + ")", NamedTextColor.GRAY)));
        holder.inventory = inv;

        int start = page * PAGE_SIZE;
        for (int i = start; i < Math.min(owned.size(), start + PAGE_SIZE); i++) {
            String key = owned.get(i);
            inv.setItem(i - start, catalog.menuIcon(key, true));
        }

        if (page > 0) {
            inv.setItem(45, nav(Material.ARROW, "Previous page"));
        }
        if (page < pages - 1) {
            inv.setItem(53, nav(Material.ARROW, "Next page"));
        }
        inv.setItem(SLOT_WEAPONS, categoryButton(Material.IRON_SWORD, "Weapons", !armour));
        inv.setItem(SLOT_ARMOUR, categoryButton(Material.DIAMOND_CHESTPLATE, "Armour", armour));
        inv.setItem(49, nav(Material.BARRIER, "Close"));
        inv.setItem(SLOT_STORE, nav(Material.EMERALD, "Store - click for a link"));
        if (charmGui != null) {
            inv.setItem(SLOT_CHARMS, nav(Material.NETHER_STAR, "StrataCharm: " + charmGui.balanceOf(player)
                    + " - click to browse the catalog"));
        }
        if (owned.isEmpty()) {
            ItemStack empty = nav(Material.PAPER, "No " + category.toLowerCase() + " skins unlocked yet");
            var meta = empty.getItemMeta();
            meta.lore(List.of(Component.text("Click to browse the StrataCharm catalog", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            empty.setItemMeta(meta);
            inv.setItem(22, empty);
        }

        player.openInventory(inv);
    }

    private ItemStack categoryButton(Material material, String label, boolean selected) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(Component.text((selected ? "✓ " : "") + label,
                selected ? NamedTextColor.AQUA : NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack nav(Material m, String label) {
        ItemStack it = new ItemStack(m);
        var meta = it.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        it.setItemMeta(meta);
        return it;
    }

    private List<String> ownedSkins(Player player) {
        boolean all = player.hasPermission("strataskins.*");
        List<String> out = new ArrayList<>();
        for (String key : catalog.keys()) {
            if (all || player.hasPermission("strataskins." + key)) {
                out.add(key);
            }
        }
        out.sort(Comparator.<String>comparingInt(catalog::menuOrder).thenComparing(Comparator.<String>naturalOrder()));
        return out;
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
        int slot = event.getRawSlot();
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if (slot == 45) {
            open(player, holder.page - 1, holder.armour);
            return;
        }
        if (slot == 53) {
            open(player, holder.page + 1, holder.armour);
            return;
        }
        if (slot == SLOT_STORE) {
            StoreLink.send(plugin, player);
            return;
        }
        if (slot == SLOT_WEAPONS) {
            open(player, 0, false);
            return;
        }
        if (slot == SLOT_ARMOUR) {
            open(player, 0, true);
            return;
        }
        if (slot == SLOT_CHARMS && charmGui != null) {
            charmGui.openCatalog(player, 0, holder.armour);
            return;
        }
        if (slot == 22 && ownedSkins(player).stream().noneMatch(key -> catalog.isArmorSkin(key) == holder.armour)
                && charmGui != null) {
            charmGui.openCatalog(player, 0, holder.armour);
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) {
            return;
        }
        ItemStack icon = event.getInventory().getItem(slot);
        if (icon == null || !icon.hasItemMeta()) {
            return;
        }
        List<String> owned = ownedSkins(player).stream()
                .filter(key -> catalog.isArmorSkin(key) == holder.armour).toList();
        int index = holder.page * PAGE_SIZE + slot;
        if (index >= owned.size()) {
            return;
        }
        String key = owned.get(index);

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir() || !catalog.canSkin(held.getType(), key)) {
            player.sendMessage(Component.text(catalog.wrongItemHint(key), NamedTextColor.RED));
            return;
        }
        if (!catalog.applySkinTo(held, key)) {
            player.sendMessage(Component.text("Couldn't apply that skin.", NamedTextColor.RED));
            return;
        }
        player.getInventory().setItemInMainHand(held);
        player.sendMessage(Component.text("Applied ", NamedTextColor.GREEN)
                .append(Component.text(catalog.displayNameOf(key), NamedTextColor.GOLD))
                .append(Component.text(" to your " + held.getType().name().toLowerCase().replace('_', ' ') + ".",
                        NamedTextColor.GREEN)));
        player.closeInventory();
    }

    static final class Holder implements InventoryHolder {
        private final int page;
        private final boolean armour;
        private Inventory inventory;

        Holder(int page, boolean armour) {
            this.page = page;
            this.armour = armour;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
