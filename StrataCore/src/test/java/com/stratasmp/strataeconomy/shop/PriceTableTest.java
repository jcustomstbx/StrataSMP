package com.stratasmp.strataeconomy.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.stratasmp.strataeconomy.StrataEconomy;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Loads the real sections/*.yml files bundled with the plugin. */
class PriceTableTest {

    @TempDir Path dataDir;
    private StrataEconomy plugin;
    private YamlConfiguration config;
    private final List<String> warnings = new ArrayList<>();

    @BeforeEach
    void setUp() {
        plugin = mock(StrataEconomy.class);
        config = new YamlConfiguration();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord r) { if (r.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()) warnings.add(r.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        when(plugin.getDataFolder()).thenReturn(dataDir.toFile());
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getConfig()).thenReturn(config);
        // saveResource copies the bundled section file out of the plugin jar, as the real one does
        doAnswer(inv -> {
            String path = inv.getArgument(0);
            try (InputStream in = PriceTableTest.class.getResourceAsStream("/modules/StrataEconomy/" + path)) {
                assertNotNull(in, "bundled resource missing: " + path);
                File out = new File(dataDir.toFile(), path);
                out.getParentFile().mkdirs();
                Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return null;
        }).when(plugin).saveResource(anyString(), anyBoolean());
    }

    /** Item check by name only: Material#isItem needs a running server. */
    private PriceTable table() {
        return new PriceTable(plugin, m -> !m.name().startsWith("LEGACY_") && !m.name().endsWith("AIR"));
    }

    @Test
    void everyBundledSectionFileLoadsWithoutWarnings() {
        table();
        assertTrue(warnings.isEmpty(), "unexpected warnings while loading prices: " + warnings);
    }

    @Test
    void explicitSectionPricesAreKept() {
        PriceTable prices = table();
        assertEquals(20, prices.baseSellPrice(Material.DIAMOND));
        assertEquals(75, prices.baseSellPrice(Material.NETHERITE_INGOT));
        assertEquals(10, prices.baseSellPrice(Material.IRON_INGOT));
        assertEquals(10, prices.baseSellPrice(Material.WHITE_WOOL), "coloured blocks keep the colored-sell rule");
    }

    @Test
    void everythingElseGetsADefaultSellPrice() {
        PriceTable prices = table();
        // STONE only had a buy price, so it used to be unsellable
        assertTrue(prices.canSell(Material.STONE));
        assertEquals(1, prices.baseSellPrice(Material.STONE));
        assertTrue(prices.canSell(Material.NETHER_STAR));
        assertTrue(prices.canSell(Material.DIAMOND_PICKAXE));
        assertTrue(prices.canSell(Material.HOPPER));
        assertTrue(prices.canSell(Material.POPPY));
    }

    @Test
    void unobtainableItemsStayUnsellable() {
        PriceTable prices = table();
        for (Material m : new Material[] {Material.BEDROCK, Material.BARRIER, Material.COMMAND_BLOCK, Material.ZOMBIE_SPAWN_EGG,
                Material.SPAWNER, Material.DEBUG_STICK}) {
            assertFalse(prices.canSell(m), m + " must not be sellable");
            assertEquals(0, prices.sellPrice(m));
        }
    }

    @Test
    void woodCraftedItemsCannotBeSoldButLogsAndPlanksCan() {
        PriceTable prices = table();
        for (Material m : new Material[] {Material.OAK_STAIRS, Material.SPRUCE_SLAB, Material.BIRCH_FENCE, Material.OAK_DOOR,
                Material.CHERRY_TRAPDOOR, Material.OAK_BUTTON, Material.OAK_PRESSURE_PLATE, Material.OAK_SIGN, Material.OAK_BOAT,
                Material.STICK, Material.BOWL, Material.LADDER, Material.BAMBOO_RAFT, Material.CRIMSON_STAIRS}) {
            assertFalse(prices.canSell(m), m + " is crafted from planks and must not sell");
        }
        assertEquals(4, prices.baseSellPrice(Material.OAK_LOG), "logs keep their explicit price");
        assertEquals(1, prices.baseSellPrice(Material.OAK_PLANKS));
        assertTrue(prices.canSell(Material.CHERRY_LOG));
        assertTrue(prices.canSell(Material.STONE_SLAB), "only wood is affected");
    }

    @Test
    void aSectionFilePriceBringsAWoodItemBack() throws Exception {
        // an explicit price always wins over the default rules
        java.nio.file.Files.createDirectories(dataDir.resolve("sections"));
        java.nio.file.Files.writeString(dataDir.resolve("sections/building_blocks.yml"),
                "sellable: true\nbuyable: true\nitems:\n  OAK_STAIRS: { sell: 2, buy: 60 }\n");

        PriceTable prices = table();

        assertEquals(2, prices.baseSellPrice(Material.OAK_STAIRS));
    }

    @Test
    void everySellableItemHasAPositivePrice() {
        PriceTable prices = table();
        for (var entry : prices.sellView().entrySet()) {
            assertTrue(entry.getValue() > 0, entry.getKey() + " is listed but priced " + entry.getValue());
        }
    }

    @Test
    void nothingSellsForMoreThanItCostsToBuy() {
        PriceTable prices = table();
        for (var entry : prices.buyView().entrySet()) {
            Material m = entry.getKey();
            if (prices.canSell(m)) {
                assertTrue(prices.baseSellPrice(m) < entry.getValue(), m + " would sell for at least its buy price");
            }
        }
    }

    @Test
    void sellingEverythingCanBeSwitchedOff() {
        config.set("shop.sell-everything", false);
        PriceTable prices = table();

        assertEquals(20, prices.baseSellPrice(Material.DIAMOND));
        assertFalse(prices.canSell(Material.NETHER_STAR), "only explicitly priced items remain");
    }

    @Test
    void theUnsellableListOverridesEveryPrice() {
        config.set("shop.unsellable", List.of("DIAMOND", "NETHER_STAR", "NOT_A_MATERIAL"));
        PriceTable prices = table();

        assertFalse(prices.canSell(Material.DIAMOND), "even an explicitly priced item can be blocked");
        assertFalse(prices.canSell(Material.NETHER_STAR));
        assertTrue(prices.canSell(Material.EMERALD));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("NOT_A_MATERIAL")), "a bad name is reported");
    }

    /** Loads the config.yml bundled in the plugin, as a real server would on first start. */
    private void useShippedConfig() throws Exception {
        try (InputStream in = PriceTableTest.class.getResourceAsStream("/modules/StrataEconomy/config.yml")) {
            assertNotNull(in);
            config.loadFromString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Test
    void shippedUnsellableListOnlyNamesRealMaterials() throws Exception {
        useShippedConfig();
        List<String> names = config.getStringList("shop.unsellable");
        assertFalse(names.isEmpty());
        for (String name : names) {
            assertNotNull(Material.matchMaterial(name), name + " in shop.unsellable is not a material");
        }
        assertEquals(names.size(), new java.util.HashSet<>(names).size(), "no duplicates in shop.unsellable");
    }

    @Test
    void farmableBlocksAndPlantsAreNotSellableWithTheShippedConfig() throws Exception {
        useShippedConfig();
        PriceTable prices = table();
        for (Material m : new Material[] {Material.STONE, Material.COBBLESTONE, Material.SNOW_BLOCK, Material.SNOWBALL,
                Material.POINTED_DRIPSTONE, Material.SCULK, Material.OAK_SAPLING, Material.OAK_LEAVES, Material.POPPY,
                Material.SUNFLOWER, Material.SHORT_GRASS, Material.VINE, Material.WHEAT_SEEDS, Material.BRAIN_CORAL,
                Material.MOSS_BLOCK, Material.CRIMSON_FUNGUS}) {
            assertFalse(prices.canSell(m), m + " is farmable and must not sell");
        }
        assertTrue(warnings.isEmpty(), "the shipped config produced warnings: " + warnings);
    }

    @Test
    void shippedUnsellableListLeavesAlreadyPricedItemsAlone() throws Exception {
        useShippedConfig();
        PriceTable prices = table();
        // priced in the section files before the sell-everything change, so the owner's choice stands
        for (Material m : new Material[] {Material.WHEAT, Material.SUGAR_CANE, Material.CACTUS, Material.BAMBOO, Material.KELP,
                Material.DEEPSLATE, Material.NETHERRACK, Material.BASALT, Material.CHORUS_FRUIT, Material.LILY_PAD}) {
            assertTrue(prices.canSell(m), m + " was priced explicitly and must still sell");
        }
        assertTrue(prices.canSell(Material.DIAMOND_PICKAXE), "everything else still sells");
        assertTrue(prices.canSell(Material.HOPPER));
    }

    @Test
    void craftedFoodNeverSellsForMoreThanItsIngredients() {
        PriceTable prices = table();
        // 9 wheat make a hay block; 8 gold nuggets + a carrot make a golden carrot; 8 nuggets + a melon slice a glistering slice
        assertTrue(prices.baseSellPrice(Material.HAY_BLOCK) <= 9 * prices.baseSellPrice(Material.WHEAT));
        assertTrue(prices.baseSellPrice(Material.GOLDEN_CARROT)
                <= prices.baseSellPrice(Material.CARROT) + 8 * prices.baseSellPrice(Material.GOLD_NUGGET));
        assertTrue(prices.baseSellPrice(Material.GLISTERING_MELON_SLICE)
                <= prices.baseSellPrice(Material.MELON_SLICE) + 8 * prices.baseSellPrice(Material.GOLD_NUGGET));
    }

    @Test
    void storageBlocksNeverSellForMoreThanTheirIngots() {
        PriceTable prices = table();
        Object[][] pairs = {{Material.IRON_BLOCK, Material.IRON_INGOT}, {Material.GOLD_BLOCK, Material.GOLD_INGOT},
                {Material.DIAMOND_BLOCK, Material.DIAMOND}, {Material.EMERALD_BLOCK, Material.EMERALD},
                {Material.NETHERITE_BLOCK, Material.NETHERITE_INGOT}, {Material.COAL_BLOCK, Material.COAL},
                {Material.COPPER_BLOCK, Material.COPPER_INGOT}, {Material.LAPIS_BLOCK, Material.LAPIS_LAZULI},
                {Material.REDSTONE_BLOCK, Material.REDSTONE}};
        for (Object[] pair : pairs) {
            int block = prices.baseSellPrice((Material) pair[0]);
            int ingot = prices.baseSellPrice((Material) pair[1]);
            assertTrue(block <= 9 * ingot, pair[0] + " sells for " + block + " but 9 x " + pair[1] + " is " + 9 * ingot);
        }
    }

    @Test
    void sellableItemsAreListedInTheirSections() {
        PriceTable prices = table();
        assertTrue(prices.sellableIn(Section.ORES_MINERALS).contains(Material.DIAMOND));
        assertTrue(prices.sellableIn(Section.MISCELLANEOUS).contains(Material.DIAMOND_PICKAXE));
        assertTrue(prices.sellableIn(Section.REDSTONE_MECHANICS).contains(Material.HOPPER));
        assertEquals(Section.ORES_MINERALS, prices.sectionOf(Material.DIAMOND, false));
        assertFalse(prices.activeSellSections().isEmpty());
        assertTrue(prices.activeSellSections().containsAll(List.of(Section.ORES_MINERALS, Section.MISCELLANEOUS)));
    }

    @Test
    void buyListIsUnchangedByTheSellDefaults() {
        PriceTable prices = table();
        assertEquals(250, prices.baseBuyPrice(Material.STONE));
        assertFalse(prices.canBuy(Material.NETHER_STAR));
        assertFalse(prices.canBuy(Material.DIAMOND_PICKAXE));
    }

    @Test
    void globalMultiplierScalesSellPricesWithinItsLimits() {
        PriceTable prices = table();
        prices.setGlobalMultiplier(1.5);
        assertEquals(30, prices.sellPrice(Material.DIAMOND));

        prices.setGlobalMultiplier(100);
        assertEquals(2.0, prices.getGlobalMultiplier(), 1e-9, "clamped to 2x");
        prices.setGlobalMultiplier(0.0);
        assertEquals(0.5, prices.getGlobalMultiplier(), 1e-9, "clamped to 0.5x");
        assertEquals(1, prices.sellPrice(Material.STONE), "a priced item never rounds down to free");
    }

    @Test
    void buyLimitsAreReadFromTheSectionFiles() {
        PriceTable prices = table();
        assertEquals(5, prices.dailyBuyLimit(Material.WHEAT));
        assertEquals(0, prices.dailyBuyLimit(Material.STONE), "no limit means unlimited");
    }
}
