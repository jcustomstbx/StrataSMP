package com.stratasmp.stratammo.quests;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /quests - a read-only menu with Daily, Weekly and Story tabs. Rewards pay automatically on completion. */
public final class QuestGui implements Listener, CommandExecutor {

    public enum Tab { DAILY, WEEKLY, STORY }

    private static final TextColor BLUE = TextColor.fromHexString("#37B6FF");
    private static final TextColor DEEP = TextColor.fromHexString("#1E6BFF");
    private static final int[] THREE = {11, 13, 15};
    private static final int[] SEVEN = {10, 11, 12, 13, 14, 15, 16};
    private static final int SLOT_DAILY = 46;
    private static final int SLOT_WEEKLY = 48;
    private static final int SLOT_STORY = 50;
    private static final int SLOT_CLOSE = 53;

    private final QuestManager quests;

    public QuestGui(QuestManager quests) {
        this.quests = quests;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        Tab tab = Tab.DAILY;
        if (args.length > 0) {
            try {
                tab = Tab.valueOf(args[0].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                if (args[0].equalsIgnoreCase("stories") || args[0].equalsIgnoreCase("chains")) {
                    tab = Tab.STORY;
                }
            }
        }
        open(player, tab);
        return true;
    }

    public void open(Player player, Tab tab) {
        Holder holder = new Holder(tab);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Quests", DEEP, TextDecoration.BOLD)
                        .append(Component.text("  " + tab.name().charAt(0) + tab.name().substring(1).toLowerCase(Locale.ROOT),
                                NamedTextColor.GRAY)));
        holder.inventory = inv;

        ItemStack pane = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        paneMeta.displayName(Component.text(" "));
        pane.setItemMeta(paneMeta);
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, pane);
        }

        switch (tab) {
            case DAILY -> fillLines(inv, THREE, quests.dailyView(player.getUniqueId()), quests.untilDailyReset(), "Daily");
            case WEEKLY -> fillLines(inv, THREE, quests.extra().weeklyView(player.getUniqueId()),
                    quests.extra().untilWeeklyReset(), "Weekly");
            case STORY -> fillChains(inv, player);
        }

        inv.setItem(SLOT_DAILY, tabButton(Material.CLOCK, "Daily quests", tab == Tab.DAILY));
        inv.setItem(SLOT_WEEKLY, tabButton(Material.ENDER_EYE, "Weekly quests", tab == Tab.WEEKLY));
        inv.setItem(SLOT_STORY, tabButton(Material.WRITTEN_BOOK, "Story quests", tab == Tab.STORY));
        inv.setItem(SLOT_CLOSE, simple(Material.BARRIER, Component.text("Close", NamedTextColor.RED)));
        player.openInventory(inv);
    }

    private void fillLines(Inventory inv, int[] slots, List<ExtraQuests.Line> lines, Duration reset, String label) {
        inv.setItem(4, simple(Material.PAPER, Component.text(label + " quests", BLUE, TextDecoration.BOLD),
                text("Resets in " + reset.toDays() + "d " + reset.toHoursPart() + "h " + reset.toMinutesPart() + "m",
                        NamedTextColor.GRAY)));
        if (lines.isEmpty()) {
            inv.setItem(22, simple(Material.PAPER, Component.text("Your quests are still loading", NamedTextColor.YELLOW),
                    text("Try again in a moment.", NamedTextColor.GRAY)));
            return;
        }
        for (int i = 0; i < lines.size() && i < slots.length; i++) {
            ExtraQuests.Line line = lines.get(i);
            ItemStack item = new ItemStack(line.done() ? Material.LIME_DYE : Material.BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(line.name(), line.done() ? NamedTextColor.GREEN : NamedTextColor.WHITE,
                    TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(detail(line));
            if (line.done()) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
            inv.setItem(slots[i], item);
        }
    }

    private void fillChains(Inventory inv, Player player) {
        inv.setItem(4, simple(Material.PAPER, Component.text("Story quests", BLUE, TextDecoration.BOLD),
                text("Finish each step to unlock the next.", NamedTextColor.GRAY)));
        List<ExtraQuests.ChainView> views = quests.extra().chainViews(player.getUniqueId());
        if (views.isEmpty()) {
            inv.setItem(22, simple(Material.PAPER, Component.text("No story quests yet", NamedTextColor.YELLOW)));
            return;
        }
        for (int i = 0; i < views.size() && i < SEVEN.length; i++) {
            ExtraQuests.ChainView view = views.get(i);
            Material icon = Material.matchMaterial(view.icon());
            ItemStack item = new ItemStack(icon == null ? Material.BOOK : icon);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(view.name(), view.done() ? NamedTextColor.GREEN : BLUE, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            if (!view.description().isEmpty()) {
                lore.add(text(view.description(), NamedTextColor.GRAY));
            }
            lore.add(text(" ", NamedTextColor.GRAY));
            if (view.done()) {
                lore.add(text("Story complete", NamedTextColor.GREEN));
            } else {
                lore.add(text("Step " + (view.step() + 1) + " of " + view.steps(), NamedTextColor.WHITE));
                lore.addAll(detail(view.current()));
                if (!view.finale().isEmpty()) {
                    lore.add(text(" ", NamedTextColor.GRAY));
                    lore.add(text("Finale reward: " + view.finale(), NamedTextColor.GOLD));
                }
            }
            meta.lore(lore);
            if (view.done()) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
            inv.setItem(SEVEN[i], item);
        }
    }

    private List<Component> detail(ExtraQuests.Line line) {
        List<Component> lore = new ArrayList<>();
        lore.add(text(line.detail(), NamedTextColor.GRAY));
        lore.add(Component.text(bar(line.progress(), line.amount()), line.done() ? NamedTextColor.GREEN : BLUE)
                .append(Component.text("  " + line.progress() + "/" + line.amount(), NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        if (!line.reward().isEmpty()) {
            lore.add(text("Reward: " + line.reward(), NamedTextColor.GOLD));
        }
        return lore;
    }

    static String bar(int progress, int amount) {
        int filled = amount <= 0 ? 0 : Math.max(0, Math.min(10, (int) Math.round(10.0 * progress / amount)));
        return "█".repeat(filled) + "░".repeat(10 - filled);
    }

    private static Component text(String value, TextColor colour) {
        return Component.text(value, colour).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack tabButton(Material material, String name, boolean selected) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text((selected ? "✓ " : "") + name, selected ? BLUE : NamedTextColor.GRAY,
                TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        if (selected) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack simple(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            meta.lore(List.of(lore));
        }
        item.setItemMeta(meta);
        return item;
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
        switch (event.getRawSlot()) {
            case SLOT_DAILY -> open(player, Tab.DAILY);
            case SLOT_WEEKLY -> open(player, Tab.WEEKLY);
            case SLOT_STORY -> open(player, Tab.STORY);
            case SLOT_CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }

    private static final class Holder implements InventoryHolder {
        private final Tab tab;
        private Inventory inventory;

        Holder(Tab tab) {
            this.tab = tab;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
