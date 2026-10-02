package com.stratasmp.stratakits;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
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
import com.stratasmp.stratacore.StrataModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The menu the hub rank NPCs open: what a rank gives, at a glance, with the kit and the store one click away. */
final class RankShowcase implements Listener {

    private static final int SLOT_TITLE = 4;
    private static final int SLOT_HOMES = 11;
    private static final int SLOT_VAULTS = 12;
    private static final int SLOT_ENDER = 13;
    private static final int SLOT_TAG = 14;
    private static final int SLOT_KIT = 15;
    private static final int SLOT_STORE = 22;
    private static final int SLOT_CLOSE = 26;
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private final StrataModule plugin;
    private final KitService service;
    private final KitGui kitGui;
    private final RankLimits limits;

    RankShowcase(StrataModule plugin, KitService service, KitGui kitGui, RankLimits limits) {
        this.plugin = plugin;
        this.service = service;
        this.kitGui = kitGui;
        this.limits = limits;
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

    private static String pretty(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String word : raw.toLowerCase().split("_")) {
            if (!word.isEmpty()) {
                sb.append(sb.length() > 0 ? " " : "").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return sb.toString();
    }

    private static String enchantText(Map<String, Integer> enchants) {
        if (enchants.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        enchants.forEach((name, level) -> parts.add(pretty(name) + (level > 0 && level < ROMAN.length ? " " + ROMAN[level] : " " + level)));
        return " &8(" + String.join(", ", parts) + ")";
    }

    private static KitItem first(Kit kit, String suffix) {
        for (KitItem item : kit.items()) {
            if (item.material().name().endsWith(suffix)) {
                return item;
            }
        }
        return null;
    }

    private static boolean isGear(KitItem item) {
        String n = item.material().name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")
                || n.endsWith("_SWORD") || n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE");
    }

    /** Built from kits.yml each time, so editing a kit updates this menu with no second place to change. */
    private List<String> kitLines(Kit kit) {
        List<String> lines = new ArrayList<>();
        KitItem helmet = first(kit, "_HELMET");
        KitItem sword = first(kit, "_SWORD");
        KitItem pick = first(kit, "_PICKAXE");
        if (helmet != null) {
            lines.add("&7Armour: &f" + pretty(helmet.material().name().split("_")[0]) + enchantText(helmet.enchants()));
        }
        if (sword != null) {
            lines.add("&7Sword: &f" + pretty(sword.material().name().split("_")[0]) + enchantText(sword.enchants()));
        }
        if (pick != null) {
            List<String> tools = new ArrayList<>();
            for (KitItem item : kit.items()) {
                String material = item.material().name();
                if (material.endsWith("_PICKAXE") || material.endsWith("_AXE") || material.endsWith("_SHOVEL") || material.endsWith("_HOE")) {
                    tools.add(pretty(material));
                }
            }
            lines.add("&7Tools: &f" + String.join(", ", tools));
        }
        for (KitItem item : kit.items()) {
            if (!isGear(item)) {
                lines.add("&7- &f" + item.amount() + "x " + pretty(item.material().name()));
            }
        }
        for (Reward reward : kit.rewards()) {
            lines.add("&7- " + reward.display());
        }
        return lines;
    }

    void open(Player player, Kit kit) {
        Holder holder = new Holder(kit);
        Inventory inv = Bukkit.createInventory(holder, 27, Text.color(kit.displayName() + " &8Rank"));
        holder.inventory = inv;
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane());
        }

        boolean paid = List.of("imperial", "ornate", "regal").contains(kit.id());
        ItemStack title = named(kit.icon(), kit.displayName(), paid
                ? List.of("&7Includes the earlier perks in", "&7the Imperial rank line.")
                : List.of("&7Includes every perk of the", "&7ranks below it as well."));
        ItemMeta titleMeta = title.getItemMeta();
        titleMeta.setEnchantmentGlintOverride(true);
        title.setItemMeta(titleMeta);
        inv.setItem(SLOT_TITLE, title);

        int homes = limits.homes(kit.id());
        if (homes > 0) {
            inv.setItem(SLOT_HOMES, named(Material.RED_BED, "&fHomes", List.of("&7Set up to &e" + homes + " &7homes", "&8with /sethome")));
        }
        int vaults = limits.vaults(kit.id());
        if (vaults > 0) {
            inv.setItem(SLOT_VAULTS, named(Material.CHEST, "&fPersonal Vaults", List.of("&7Up to &e" + vaults + " &7private vaults", "&8open with /pv")));
        }
        int rows = plugin.getConfig().getInt("showcase.ender-chest-rows", 6);
        inv.setItem(SLOT_ENDER, named(Material.ENDER_CHEST, "&fEnder Chest", List.of("&e" + rows + " rows &7of storage")));
        inv.setItem(SLOT_TAG, named(Material.NAME_TAG, "&fRank Tag", List.of("&7Your rank tag in chat, the tab list", "&7and the scoreboard.")));

        if (paid) {
            inv.setItem(18, named(Material.PINK_DYE, "&dCosmetic perks", List.of(
                    "&7Custom name colour and three chat colours", "&7/nick, /ec and strata particles",
                    kit.id().equals("regal") ? "&7Regal: three particles and a join message" : "&7Inherited by Ornate and Regal")));
            if (kit.id().equals("ornate") || kit.id().equals("regal")) {
                inv.setItem(19, named(Material.EMERALD, "&aEconomy perks", List.of(
                        "&7/sellall in SMP worlds",
                        kit.id().equals("regal") ? "&713 auction listings, 9 buy orders" : "&78 auction listings, 6 buy orders",
                        kit.id().equals("regal") ? "&75 featured listings for 24 hours" : "&7Earn Stratas through trading")));
                inv.setItem(20, named(Material.COMPASS, "&bTravel perk", List.of(
                        "&7/rtp in the SMP overworld", "&790-second cooldown", "&73.75-second stationary warmup")));
            }
            if (kit.id().equals("regal")) {
                inv.setItem(21, named(Material.ANVIL, "&5Regal commands", List.of(
                        "&7/name for the item in hand", "&7/repair one held item every 24 hours")));
            }
        }

        List<String> kitLore = new ArrayList<>();
        kitLore.add("&7Claimable every 72 hours on the SMP:");
        kitLore.addAll(kitLines(kit));
        kitLore.add("");
        kitLore.add("&eClick to preview the full kit");
        ItemStack kitButton = named(first(kit, "_CHESTPLATE") != null ? first(kit, "_CHESTPLATE").material() : Material.CHEST, "&fRank Kit", kitLore);
        inv.setItem(SLOT_KIT, kitButton);

        String url = plugin.getConfig().getString("showcase.store-url", "https://stratasmp.com/store");
        inv.setItem(SLOT_STORE, named(Material.EMERALD, "&aGet this rank", List.of("&7" + url.replace("https://", ""), "", "&eClick for the link")));
        inv.setItem(SLOT_CLOSE, named(Material.BARRIER, "&cClose", List.of()));
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
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getInventory()) {
            return;
        }
        switch (event.getSlot()) {
            case SLOT_KIT -> kitGui.open(player, holder.kit);
            case SLOT_STORE -> {
                String url = plugin.getConfig().getString("showcase.store-url", "https://stratasmp.com/store");
                player.closeInventory();
                player.sendMessage(Component.text("Get the ", NamedTextColor.GRAY)
                        .append(Text.color(holder.kit.displayName()))
                        .append(Component.text(" rank: ", NamedTextColor.GRAY))
                        .append(Component.text(url, NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url))));
            }
            case SLOT_CLOSE -> player.closeInventory();
            default -> { }
        }
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
