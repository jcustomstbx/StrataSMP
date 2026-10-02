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

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Rules about which items may change hands, and the helpers that move them safely. */
public final class TradeItems {

    private static final NamespacedKey SOULBOUND = NamespacedKey.fromString("strataweapons:soulbound");

    private final Set<Material> blocked = new HashSet<>();

    public TradeItems(List<String> blockedMaterials) {
        for (var name : blockedMaterials) {
            var material = Material.matchMaterial(name);
            if (material != null) {
                blocked.add(material);
            }
        }
    }

    /** Why this item can't be traded, or null when it can. */
    public String problemWith(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "That slot is empty.";
        }
        if (blocked.contains(stack.getType())) {
            return "That item can't be traded.";
        }
        if (isSoulbound(stack)) {
            return "Kit and faction items are bound to you and can't be traded.";
        }
        if (hidesSoulbound(stack)) {
            return "That contains a kit or faction item, which can't be traded.";
        }
        return null;
    }

    private static boolean isSoulbound(ItemStack stack) {
        var meta = stack.getItemMeta();
        return meta != null && SOULBOUND != null
            && meta.getPersistentDataContainer().has(SOULBOUND, PersistentDataType.BYTE);
    }

    private boolean hidesSoulbound(ItemStack stack) {
        var meta = stack.getItemMeta();
        if (meta instanceof BlockStateMeta blockMeta && blockMeta.getBlockState() instanceof ShulkerBox box) {
            for (var inside : box.getInventory().getContents()) {
                if (inside != null && !inside.getType().isAir()
                    && (isSoulbound(inside) || blocked.contains(inside.getType()) || hidesSoulbound(inside))) {
                    return true;
                }
            }
        }
        if (meta instanceof BundleMeta bundle) {
            for (var inside : bundle.getItems()) {
                if (isSoulbound(inside) || blocked.contains(inside.getType()) || hidesSoulbound(inside)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True if the player could take all of these into their inventory right now. */
    public static boolean canHold(Player player, List<ItemStack> items) {
        var scratch = Bukkit.createInventory(null, 36);
        var current = player.getInventory().getStorageContents();
        for (int i = 0; i < 36 && i < current.length; i++) {
            scratch.setItem(i, current[i] == null ? null : current[i].clone());
        }
        for (var item : items) {
            if (!scratch.addItem(item.clone()).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Gives the items to the player; anything that doesn't fit is dropped at their feet, theirs alone to pick up. */
    public static void give(Player player, List<ItemStack> items) {
        for (var item : items) {
            var leftover = player.getInventory().addItem(item.clone());
            for (var rest : leftover.values()) {
                var dropped = player.getWorld().dropItem(player.getLocation(), rest);
                dropped.setOwner(player.getUniqueId());
            }
        }
    }

    public static String describe(List<ItemStack> items) {
        if (items.isEmpty()) {
            return "nothing";
        }
        var text = new StringBuilder();
        for (var item : items) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(item.getAmount()).append("x ").append(item.getType().name().toLowerCase(Locale.ROOT));
            var meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                text.append(" [").append(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(meta.displayName())).append(']');
            }
            if (meta != null && meta.hasEnchants()) {
                text.append(" (enchanted)");
            }
        }
        return text.toString();
    }
}
