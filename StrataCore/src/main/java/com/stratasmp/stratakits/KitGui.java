package com.stratasmp.stratakits;

import net.kyori.adventure.text.Component;
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

/** /kit - the kit contents on top, a tab per rank along row 5 to preview the others, claim button bottom middle. */
final class KitGui implements Listener {

    private static final int ITEM_SLOTS = 36;
    private static final int TABS_ROW = 36;
    private static final int SLOT_INFO = 45;
    private static final int SLOT_CLAIM = 49;
    private static final int SLOT_CLOSE = 53;
    // Reserve the action buttons on the bottom row while allowing more than nine rank kits.
    private static final int[] TAB_SLOTS = {36, 37, 38, 39, 40, 41, 42, 43, 44, 46, 47, 48, 50, 51, 52};

    private final KitService service;

    KitGui(KitService service) {
        this.service = service;
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.item(name));
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(Text::item).toList());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack pane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }

    void open(Player player, Kit viewing) {
        Kit own = service.catalog().kitFor(player);
        Holder holder = new Holder(viewing);
        Inventory inv = Bukkit.createInventory(holder, 54, Text.color(viewing.displayName() + " &8Kit"));
        holder.inventory = inv;

        int slot = 0;
        for (KitItem item : viewing.items()) {
            if (slot >= ITEM_SLOTS) {
                break;
            }
            ItemStack icon = item.build(service.logger()).get(0);
            if (item.amount() > icon.getAmount()) {
                ItemMeta meta = icon.getItemMeta();
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(Text.item("&7Total: &fx" + item.amount()));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }
            inv.setItem(slot++, icon);
        }
        for (Reward reward : viewing.rewards()) {
            if (slot >= ITEM_SLOTS) {
                break;
            }
            inv.setItem(slot++, named(reward.icon(), reward.display(), List.of("&7Delivered when you claim")));
        }

        for (int i = TABS_ROW; i < 54; i++) {
            inv.setItem(i, pane());
        }
        List<Kit> ladder = service.catalog().ascending();
        for (int i = 0; i < ladder.size() && i < TAB_SLOTS.length; i++) {
            Kit kit = ladder.get(i);
            List<String> lore = new ArrayList<>();
            lore.add(own != null && own.id().equals(kit.id()) ? "&aYour rank's kit" : "&7Click to preview");
            ItemStack tab = named(kit.icon(), kit.displayName(), lore);
            if (kit.id().equals(viewing.id())) {
                ItemMeta meta = tab.getItemMeta();
                meta.setEnchantmentGlintOverride(true);
                tab.setItemMeta(meta);
            }
            inv.setItem(TAB_SLOTS[i], tab);
        }

        inv.setItem(SLOT_INFO, named(Material.PAPER, "&eKits",
                List.of("&7Claim your rank's kit every 72 hours.", "&7Ranking up unlocks the next kit straight away.")));
        inv.setItem(SLOT_CLAIM, claimButton(player, own, viewing));
        inv.setItem(SLOT_CLOSE, named(Material.BARRIER, "&cClose", List.of()));
        player.openInventory(inv);
    }

    private ItemStack claimButton(Player player, Kit own, Kit viewing) {
        if (own == null) {
            return named(Material.RED_STAINED_GLASS_PANE, "&cNo rank", List.of("&7Kits come with a rank - see /store."));
        }
        if (!own.id().equals(viewing.id())) {
            return named(Material.GRAY_STAINED_GLASS_PANE, "&7Preview only",
                    List.of("&7This isn't your rank's kit.", "&7Open your own tab to claim it."));
        }
        if (!service.canClaimHere(player)) {
            return named(Material.RED_STAINED_GLASS_PANE, "&cSMP only", List.of("&7Kits can only be claimed on the SMP."));
        }
        long wait = service.remaining(player, own);
        if (wait > 0) {
            return named(Material.CLOCK, "&cOn cooldown", List.of("&7Ready again in &f" + Text.duration(wait)));
        }
        int needed = own.stacksNeeded();
        return named(Material.EMERALD_BLOCK, "&a&lClaim kit", List.of("&7Needs &f" + needed + " &7free inventory slots.", "&eClick to claim"));
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
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getInventory()) {
            return;
        }
        int slot = event.getSlot();
        List<Kit> ladder = service.catalog().ascending();

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
        } else if (tabIndex(slot) >= 0 && tabIndex(slot) < ladder.size()) {
            open(player, ladder.get(tabIndex(slot)));
        } else if (slot == SLOT_CLAIM) {
            Kit own = service.catalog().kitFor(player);
            if (own == null || !own.id().equals(holder.kit.id())) {
                return;
            }
            service.claim(player);
            open(player, own);
        }
    }

    private static int tabIndex(int slot) {
        for (int i = 0; i < TAB_SLOTS.length; i++) if (TAB_SLOTS[i] == slot) return i;
        return -1;
    }

    static final class Holder implements InventoryHolder {
        private final Kit kit;
        private Inventory inventory;

        Holder(Kit kit) {
            this.kit = kit;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
