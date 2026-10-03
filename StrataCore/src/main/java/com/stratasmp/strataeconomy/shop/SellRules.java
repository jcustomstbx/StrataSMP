package com.stratasmp.strataeconomy.shop;

import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Which individual stacks may be sold. The price depends only on the material, so anything that carries more than
 * its material is refused: custom-model items (weapons, skins), named or lored items such as rank-kit gear, and
 * containers that still hold items (selling one would silently destroy the contents).
 */
public final class SellRules {
    private SellRules() {}

    public static boolean eligible(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta.hasCustomModelData() || meta.hasDisplayName() || meta.hasLore()) {
            return false;
        }
        if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
            return false;
        }
        if (meta instanceof BlockStateMeta state && state.hasBlockState()
                && state.getBlockState() instanceof Container container && !container.getInventory().isEmpty()) {
            return false;
        }
        return true;
    }
}
