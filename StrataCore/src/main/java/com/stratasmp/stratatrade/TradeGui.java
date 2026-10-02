/*
 * Copyright (c) 2026 JCustoms. All Rights Reserved.
 *
 * This file is proprietary and confidential. No use, copying, modification,
 * or distribution of this file or its compiled output, by any means, is
 * permitted without the prior written permission of JCustoms.
 *
 * See the LICENSE file distributed with this project for the full terms.
 */
package com.stratasmp.stratatrade;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Layout, six rows of nine. Rows 0-3: your offer on the left four columns, theirs on the right four,
 * a divider between. Row 4 is coins and status, row 5 is the buttons.
 */
public final class TradeGui {

    public static final int OFFER_SLOTS = 16;

    public static final int COINS_YOURS = 36;
    public static final int COINS_SMALL = 37;
    public static final int COINS_MEDIUM = 38;
    public static final int COINS_LARGE = 39;
    public static final int NOTICE = 40;
    public static final int COINS_THEIRS = 44;

    public static final int CANCEL = 45;
    public static final int INFO = 47;
    public static final int ACCEPT = 49;
    public static final int PARTNER = 51;

    public static final int STEP_SMALL = 100;
    public static final int STEP_MEDIUM = 1_000;
    public static final int STEP_LARGE = 10_000;

    private TradeGui() {
    }

    public static int yourSlot(int index) {
        return (index / 4) * 9 + index % 4;
    }

    public static int theirSlot(int index) {
        return (index / 4) * 9 + 5 + index % 4;
    }

    /** Index into your own offer for a clicked slot, or -1 when the slot isn't part of it. */
    public static int yourIndex(int slot) {
        return slot >= 0 && slot < 36 && slot % 9 < 4 ? (slot / 9) * 4 + slot % 9 : -1;
    }

    public static void render(TradeSession session, TradeSession.Side me, long now, long delayMillis) {
        var other = session.otherOf(me);
        var inventory = me.inventory;
        var confirm = session.stage == TradeSession.Stage.CONFIRM;
        inventory.clear();

        for (int row = 0; row < 4; row++) {
            inventory.setItem(row * 9 + 4, pane(Material.GRAY_STAINED_GLASS_PANE));
        }
        for (int slot = 36; slot < 54; slot++) {
            inventory.setItem(slot, pane(Material.BLACK_STAINED_GLASS_PANE));
        }

        for (int i = 0; i < me.offer.size(); i++) {
            inventory.setItem(yourSlot(i), confirm ? described(me.offer.get(i), false)
                : withHint(me.offer.get(i), "Click to take it back"));
        }
        for (int i = 0; i < other.offer.size(); i++) {
            inventory.setItem(theirSlot(i), described(other.offer.get(i), true));
        }

        renderCoins(inventory, session, me, other, confirm);
        renderButtons(inventory, session, me, other, now, delayMillis);
    }

    /** Only the parts that change every second, so the countdown doesn't rebuild the whole window. */
    public static void renderButtons(org.bukkit.inventory.Inventory inventory, TradeSession session,
                                     TradeSession.Side me, TradeSession.Side other, long now, long delayMillis) {
        var confirm = session.stage == TradeSession.Stage.CONFIRM;
        inventory.setItem(CANCEL, button(Material.RED_CONCRETE, confirm ? "Decline trade" : "Cancel trade",
            NamedTextColor.RED, "Everything goes back to its owner."));
        inventory.setItem(INFO, button(Material.BOOK, "How this works", NamedTextColor.AQUA,
            confirm ? "Check both sides one last time." : "Add items by clicking them in your inventory.",
            confirm ? "Both of you must accept to finish." : "Any change resets both accepts.",
            "Nothing is exchanged until you both accept twice."));

        var remaining = Math.max(0, me.unlockAt - now);
        if (remaining > 0) {
            var seconds = (int) Math.ceil(remaining / 1000.0);
            inventory.setItem(ACCEPT, button(Material.GRAY_CONCRETE, "Accept in " + seconds + "s", NamedTextColor.GRAY,
                "Read the offer carefully first.", "Accept unlocks after a short wait."));
        } else if (me.accepted) {
            inventory.setItem(ACCEPT, button(Material.EMERALD_BLOCK, "Accepted", NamedTextColor.GREEN,
                "Waiting for " + other.player.getName() + "...", "Click to take back your accept."));
        } else {
            inventory.setItem(ACCEPT, button(Material.LIME_CONCRETE, confirm ? "CONFIRM TRADE" : "ACCEPT",
                NamedTextColor.GREEN, confirm ? "Click to complete the trade." : "Click to move on to the confirmation."));
        }

        var head = new ItemStack(Material.PLAYER_HEAD);
        var meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(other.player);
        meta.displayName(line(other.player.getName(), NamedTextColor.WHITE));
        meta.lore(List.of(other.accepted ? line("Has accepted", NamedTextColor.GREEN)
            : line("Hasn't accepted yet", NamedTextColor.RED)));
        head.setItemMeta(meta);
        inventory.setItem(PARTNER, head);
    }

    private static void renderCoins(org.bukkit.inventory.Inventory inventory, TradeSession session,
                                    TradeSession.Side me, TradeSession.Side other, boolean confirm) {
        if (!confirm) {
            inventory.setItem(COINS_YOURS, button(Material.GOLD_INGOT, "Your Stratas: " + format(me.coins),
                NamedTextColor.GOLD, "Click to set back to 0."));
            inventory.setItem(COINS_SMALL, stepButton(STEP_SMALL));
            inventory.setItem(COINS_MEDIUM, stepButton(STEP_MEDIUM));
            inventory.setItem(COINS_LARGE, stepButton(STEP_LARGE));
            inventory.setItem(NOTICE, button(Material.PAPER, "Offer stage", NamedTextColor.YELLOW,
                "Put in what you want to trade."));
        } else {
            inventory.setItem(COINS_YOURS, button(Material.GOLD_INGOT, "You give: " + format(me.coins) + " Stratas",
                NamedTextColor.GOLD));
            if (other.offer.isEmpty() && other.coins == 0) {
                inventory.setItem(NOTICE, button(Material.RED_STAINED_GLASS_PANE, "YOU RECEIVE NOTHING",
                    NamedTextColor.RED, other.player.getName() + " isn't giving you anything.",
                    "Decline unless that is what you agreed."));
            } else {
                inventory.setItem(NOTICE, button(Material.YELLOW_STAINED_GLASS_PANE, "Check carefully",
                    NamedTextColor.YELLOW, "This is the final offer.", "Items show their real type."));
            }
        }
        inventory.setItem(COINS_THEIRS, button(Material.GOLD_INGOT,
            (confirm ? "You receive: " : other.player.getName() + "'s Stratas: ") + format(other.coins)
                + (confirm ? " Stratas" : ""), NamedTextColor.GOLD));
    }

    private static ItemStack stepButton(int step) {
        var stack = button(Material.GOLD_NUGGET, "+ / - " + format(step), NamedTextColor.GOLD,
            "Left-click: add " + format(step), "Right-click: remove " + format(step),
            "Shift-click: ten times that");
        return stack;
    }

    /** Shows the real item type underneath, so a renamed item can't pose as something else. */
    private static ItemStack described(ItemStack original, boolean theirs) {
        var stack = original.clone();
        var meta = stack.getItemMeta();
        var lore = new ArrayList<Component>();
        if (meta.lore() != null) {
            lore.addAll(meta.lore());
        }
        lore.add(Component.empty());
        lore.add(line("Real item: " + pretty(stack.getType()) + " x" + stack.getAmount(), NamedTextColor.DARK_GRAY));
        if (theirs) {
            lore.add(line("Offered by your partner", NamedTextColor.DARK_GRAY));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack withHint(ItemStack original, String hint) {
        var stack = described(original, false);
        var meta = stack.getItemMeta();
        var lore = new ArrayList<>(meta.lore());
        lore.add(line(hint, NamedTextColor.GRAY));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack pane(Material material) {
        var stack = new ItemStack(material);
        var meta = stack.getItemMeta();
        meta.displayName(Component.text(" "));
        meta.setHideTooltip(true);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack button(Material material, String name, NamedTextColor color, String... description) {
        var stack = new ItemStack(material);
        var meta = stack.getItemMeta();
        meta.displayName(line(name, color).decorate(TextDecoration.BOLD));
        var lore = new ArrayList<Component>();
        for (var text : description) {
            lore.add(line(text, NamedTextColor.GRAY));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    public static String format(int number) {
        return String.format(Locale.US, "%,d", number);
    }

    private static String pretty(Material material) {
        var words = material.name().toLowerCase(Locale.ROOT).split("_");
        var text = new StringBuilder();
        for (var word : words) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return text.toString();
    }
}
