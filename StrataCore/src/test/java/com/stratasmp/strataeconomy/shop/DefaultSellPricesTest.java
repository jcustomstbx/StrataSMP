package com.stratasmp.strataeconomy.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DefaultSellPricesTest {

    @Test
    void everyNamedItemInTheFixedTableIsARealMaterial() {
        // a typo here would silently leave that item on the 1-strata fallback
        for (String name : DefaultSellPrices.fixedNames()) {
            assertNotNull(Material.getMaterial(name), name + " is not a Material in this server version");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"AIR", "CAVE_AIR", "BARRIER", "BEDROCK", "COMMAND_BLOCK", "STRUCTURE_BLOCK", "SPAWNER",
            "ZOMBIE_SPAWN_EGG", "ALLAY_SPAWN_EGG", "DEBUG_STICK", "DRAGON_EGG", "REINFORCED_DEEPSLATE", "LEGACY_STONE"})
    void unobtainableItemsAreNeverSellable(String name) {
        assertNull(DefaultSellPrices.of(name));
        assertTrue(DefaultSellPrices.isNever(name));
    }

    @Test
    void ordinaryItemsGetAPositivePrice() {
        for (String name : new String[] {"STONE", "DIRT", "GLASS", "CHEST", "WHITE_BED", "POPPY", "OAK_SAPLING"}) {
            DefaultSellPrices.Entry entry = DefaultSellPrices.of(name);
            assertNotNull(entry, name);
            assertTrue(entry.price() > 0, name);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"OAK_STAIRS", "SPRUCE_SLAB", "BIRCH_FENCE", "JUNGLE_FENCE_GATE", "ACACIA_DOOR", "DARK_OAK_TRAPDOOR",
            "MANGROVE_BUTTON", "CHERRY_PRESSURE_PLATE", "PALE_OAK_SIGN", "OAK_HANGING_SIGN", "BAMBOO_RAFT", "BAMBOO_CHEST_RAFT",
            "OAK_BOAT", "OAK_CHEST_BOAT", "CRIMSON_STAIRS", "WARPED_DOOR", "BAMBOO_MOSAIC", "BAMBOO_MOSAIC_STAIRS",
            "BAMBOO_MOSAIC_SLAB", "STICK", "BOWL", "LADDER", "oak_slab"})
    void itemsCraftedFromPlanksAreNotSellableByDefault(String name) {
        assertTrue(DefaultSellPrices.isWoodCrafted(name));
        assertNull(DefaultSellPrices.of(name), name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OAK_LOG", "OAK_PLANKS", "OAK_WOOD", "STRIPPED_OAK_LOG", "OAK_SAPLING", "OAK_LEAVES", "WOODEN_SWORD",
            "WOODEN_PICKAXE", "STONE_BUTTON", "STONE_SLAB", "STONE_STAIRS", "STONE_PRESSURE_PLATE", "COBBLESTONE_SLAB",
            "CRIMSON_STEM", "BAMBOO_BLOCK", "CHEST", "CRAFTING_TABLE", "BARREL", "SCAFFOLDING"})
    void raw_wood_and_non_wood_items_are_unaffected(String name) {
        assertFalse(DefaultSellPrices.isWoodCrafted(name), name);
    }

    @Test
    void namesAreCaseInsensitive() {
        assertEquals(DefaultSellPrices.of("NETHER_STAR"), DefaultSellPrices.of("nether_star"));
    }

    @Test
    void toolAndArmourPricesRiseWithTheTier() {
        String[] tiers = {"WOODEN", "IRON", "DIAMOND", "NETHERITE"};
        for (String piece : new String[] {"SWORD", "PICKAXE", "CHESTPLATE"}) {
            int previous = 0;
            for (String tier : tiers) {
                String name = tier + "_" + piece;
                // there are no wooden armour pieces
                if (!exists(name)) continue;
                int price = DefaultSellPrices.of(name).price();
                assertTrue(price > previous, name + " should sell for more than the tier before it");
                previous = price;
            }
        }
    }

    @Test
    void gearNeverSellsForMoreThanItsIngredientsAreWorth() {
        // ingredient prices from ores_minerals.yml: iron 10, gold 10, copper 7, diamond 20, netherite ingot 75
        int[][] counts = {{2}, {3}, {3}, {1}, {2}, {5}, {8}, {7}, {4}};
        String[] pieces = {"SWORD", "PICKAXE", "AXE", "SHOVEL", "HOE", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
        String[] tiers = {"IRON", "GOLDEN", "COPPER", "DIAMOND", "NETHERITE"};
        int[] unit = {10, 10, 7, 20, 20};
        for (int t = 0; t < tiers.length; t++) {
            for (int p = 0; p < pieces.length; p++) {
                String name = tiers[t] + "_" + pieces[p];
                if (Material.getMaterial(name) == null) continue;
                int ingredients = counts[p][0] * unit[t] + (tiers[t].equals("NETHERITE") ? 75 : 0);
                int price = DefaultSellPrices.of(name).price();
                assertTrue(price <= ingredients, name + " sells for " + price + " but its ingredients sell for " + ingredients);
            }
        }
    }

    @Test
    void copperItemsMadeFromIngotsStayBelowTheIngot() {
        // one ingot (7) makes nine nuggets, which cannot be priced below 9 at the 1-strata floor
        assertNull(DefaultSellPrices.of("COPPER_NUGGET"));
        assertTrue(DefaultSellPrices.of("COPPER_BARS").price() <= 1);
        assertTrue(DefaultSellPrices.of("CUT_COPPER").price() <= 7);
    }

    @Test
    void categoriesAreSensible() {
        assertEquals(Section.MISCELLANEOUS, DefaultSellPrices.of("DIAMOND_SWORD").section());
        assertEquals(Section.REDSTONE_MECHANICS, DefaultSellPrices.of("HOPPER").section());
        assertEquals(Section.FARMING_DROPS, DefaultSellPrices.of("COOKED_BEEF").section());
        assertEquals(Section.FARMING_DROPS, DefaultSellPrices.of("RED_DYE").section());
        assertEquals(Section.MOB_DROPS, DefaultSellPrices.of("ZOMBIE_HEAD").section());
        assertEquals(Section.BUILDING_BLOCKS, DefaultSellPrices.of("STONE").section());
    }

    @Test
    void rareItemsAreWorthMoreThanCommonOnes() {
        assertTrue(DefaultSellPrices.of("NETHER_STAR").price() > DefaultSellPrices.of("GOLDEN_APPLE").price());
        assertTrue(DefaultSellPrices.of("ENCHANTED_GOLDEN_APPLE").price() > DefaultSellPrices.of("GOLDEN_APPLE").price());
        assertTrue(DefaultSellPrices.of("NETHERITE_UPGRADE_SMITHING_TEMPLATE").price()
                > DefaultSellPrices.of("SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE").price());
        assertTrue(DefaultSellPrices.of("MUSIC_DISC_13").price() > DefaultSellPrices.of("BOOK").price());
    }

    @Test
    void noMaterialGetsAnAbsurdDefault() {
        for (Material m : Material.values()) {
            DefaultSellPrices.Entry entry = DefaultSellPrices.of(m.name());
            if (entry != null) {
                assertTrue(entry.price() >= 1 && entry.price() <= 1000, m + " priced " + entry.price());
                assertNotNull(entry.section(), m.name());
            }
        }
    }

    private static boolean exists(String name) {
        return Material.getMaterial(name) != null;
    }
}
