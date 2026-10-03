package com.stratasmp.strataeconomy.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        for (String name : new String[] {"STONE", "DIRT", "OAK_STAIRS", "GLASS", "CHEST", "WHITE_BED", "POPPY", "OAK_SAPLING"}) {
            DefaultSellPrices.Entry entry = DefaultSellPrices.of(name);
            assertNotNull(entry, name);
            assertTrue(entry.price() > 0, name);
        }
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
    void gearNeverSellsForMoreThanItsIngotsAreWorth() {
        // iron ingot 10, diamond 20, gold 10 (see ores_minerals.yml): a sword needs at least two of them
        assertTrue(DefaultSellPrices.of("IRON_SWORD").price() <= 2 * 10);
        assertTrue(DefaultSellPrices.of("GOLDEN_SWORD").price() <= 2 * 10);
        assertTrue(DefaultSellPrices.of("DIAMOND_SWORD").price() <= 2 * 20 * 2);
        assertTrue(DefaultSellPrices.of("IRON_CHESTPLATE").price() <= 8 * 10);
        assertTrue(DefaultSellPrices.of("DIAMOND_CHESTPLATE").price() <= 8 * 20);
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
