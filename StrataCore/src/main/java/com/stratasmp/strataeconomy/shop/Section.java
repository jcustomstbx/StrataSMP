package com.stratasmp.strataeconomy.shop;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

/**
 * The six shop/sell catalog groupings. Each one owns a {@code sections/<id>.yml}
 * resource file. sellable/buyable are category-level switches: an item's price
 * in its file is ignored on the disabled side, so re-enabling a whole category
 * later is one boolean flip rather than re-entering every price.
 */
public enum Section {

    BUILDING_BLOCKS("building_blocks", "Building Blocks", Material.BRICKS, NamedTextColor.GOLD, true, true),
    FARMING_DROPS("farming_drops", "Farming Drops", Material.WHEAT, NamedTextColor.GREEN, true, true),
    MOB_DROPS("mob_drops", "Mob Drops", Material.ROTTEN_FLESH, NamedTextColor.DARK_RED, true, false),
    ORES_MINERALS("ores_minerals", "Ores & Minerals", Material.DIAMOND, NamedTextColor.AQUA, true, false),
    REDSTONE_MECHANICS("redstone_mechanics", "Redstone & Mechanics", Material.REDSTONE, NamedTextColor.RED, true, false),
    MISCELLANEOUS("miscellaneous", "Miscellaneous", Material.CHEST, NamedTextColor.LIGHT_PURPLE, true, true);

    public final String id;
    public final String displayName;
    public final Material icon;
    public final NamedTextColor color;
    public final boolean sellable;
    public final boolean buyable;

    Section(String id, String displayName, Material icon, NamedTextColor color, boolean sellable, boolean buyable) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
        this.sellable = sellable;
        this.buyable = buyable;
    }
}
