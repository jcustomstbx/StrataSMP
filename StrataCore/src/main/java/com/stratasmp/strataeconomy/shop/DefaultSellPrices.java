package com.stratasmp.strataeconomy.shop;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fallback sell prices so that every obtainable item can be sold, not just the ones listed in sections/*.yml.
 * Anything priced in a section file keeps that price; this only fills the gaps. Pricing is by name so it needs no
 * running server. Prices are deliberately low placeholders, the section files are where real tuning happens, and
 * an item can be taken out of the sell list with {@code shop.unsellable} in config.yml.
 */
public final class DefaultSellPrices {

    /** Price and category for one item. */
    public record Entry(int price, Section section) {}

    private static final int FALLBACK_PRICE = 1;

    private static final Map<String, Entry> FIXED = new HashMap<>();
    /** What one ingredient of each gear tier sells for (see ores_minerals.yml); gear is priced below its ingredients. */
    private static final Map<String, Integer> TIERS = new HashMap<>();
    /** How many of that ingredient each piece needs. */
    private static final Map<String, Integer> PIECE_COUNT = new HashMap<>();
    private static final int NETHERITE_INGOT_VALUE = 75;
    private static final String[] PIECES = {
            "_SWORD", "_PICKAXE", "_AXE", "_SHOVEL", "_HOE", "_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS", "_SPEAR"};
    private static final Set<String> NEVER = new HashSet<>();
    private static final String[] WOODS = {"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK", "MANGROVE", "CHERRY",
            "PALE_OAK", "BAMBOO", "CRIMSON", "WARPED"};
    /** What planks are crafted into. A log is worth about four planks, so these cannot be priced at 1 without profit. */
    private static final String[] WOOD_CRAFTED = {"_STAIRS", "_SLAB", "_FENCE", "_FENCE_GATE", "_DOOR", "_TRAPDOOR", "_BUTTON",
            "_PRESSURE_PLATE", "_SIGN", "_HANGING_SIGN", "_BOAT", "_CHEST_BOAT", "_RAFT", "_CHEST_RAFT"};

    private static void fix(int price, Section section, String... names) {
        for (String name : names) FIXED.put(name, new Entry(price, section));
    }

    static {
        // creative-only, technical or unobtainable items are never sellable
        for (String n : new String[] {
                "AIR", "CAVE_AIR", "VOID_AIR", "BARRIER", "BEDROCK", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK",
                "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART", "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "LIGHT",
                "DEBUG_STICK", "KNOWLEDGE_BOOK", "SPAWNER", "TRIAL_SPAWNER", "VAULT", "REINFORCED_DEEPSLATE",
                "END_PORTAL_FRAME", "COPPER_NUGGET", "BUDDING_AMETHYST", "DRAGON_EGG", "FARMLAND", "PETRIFIED_OAK_SLAB", "WRITTEN_BOOK",
                "FILLED_MAP", "TEST_BLOCK", "TEST_INSTANCE_BLOCK", "CHORUS_PLANT"}) {
            NEVER.add(n);
        }

        TIERS.put("WOODEN", 1);
        TIERS.put("STONE", 1);
        TIERS.put("LEATHER", 4);
        TIERS.put("CHAINMAIL", 3);
        TIERS.put("IRON", 10);
        TIERS.put("GOLDEN", 10);
        TIERS.put("COPPER", 7);
        TIERS.put("DIAMOND", 20);
        TIERS.put("NETHERITE", 20); // netherite gear is diamond gear plus an ingot, see gearPrice
        PIECE_COUNT.put("_SWORD", 2);
        PIECE_COUNT.put("_PICKAXE", 3);
        PIECE_COUNT.put("_AXE", 3);
        PIECE_COUNT.put("_SHOVEL", 1);
        PIECE_COUNT.put("_HOE", 2);
        PIECE_COUNT.put("_HELMET", 5);
        PIECE_COUNT.put("_CHESTPLATE", 8);
        PIECE_COUNT.put("_LEGGINGS", 7);
        PIECE_COUNT.put("_BOOTS", 4);
        PIECE_COUNT.put("_SPEAR", 2);

        fix(500, Section.MISCELLANEOUS, "NETHER_STAR");
        fix(800, Section.MISCELLANEOUS, "ELYTRA");
        fix(120, Section.MISCELLANEOUS, "TRIDENT");
        fix(400, Section.MISCELLANEOUS, "MACE", "CONDUIT");
        fix(300, Section.MISCELLANEOUS, "HEAVY_CORE", "BEACON");
        fix(100, Section.MISCELLANEOUS, "RECOVERY_COMPASS", "SNIFFER_EGG");
        fix(60, Section.MISCELLANEOUS, "BELL", "GOLDEN_APPLE", "LODESTONE");
        fix(600, Section.MISCELLANEOUS, "ENCHANTED_GOLDEN_APPLE");
        fix(40, Section.MISCELLANEOUS, "TURTLE_HELMET", "ENCHANTING_TABLE", "END_CRYSTAL", "OMINOUS_BOTTLE", "TRIAL_KEY",
                "CRAFTER", "RESPAWN_ANCHOR", "SLIME_BLOCK");
        fix(80, Section.MISCELLANEOUS, "OMINOUS_TRIAL_KEY");
        fix(25, Section.MISCELLANEOUS, "ENCHANTED_BOOK", "BLAST_FURNACE", "JUKEBOX", "ANVIL", "WOLF_ARMOR", "BUCKET",
                "WATER_BUCKET", "LAVA_BUCKET", "MILK_BUCKET", "POWDER_SNOW_BUCKET", "MINECART", "CALIBRATED_SCULK_SENSOR");
        fix(30, Section.MISCELLANEOUS, "AXOLOTL_BUCKET", "COD_BUCKET", "SALMON_BUCKET", "PUFFERFISH_BUCKET",
                "TROPICAL_FISH_BUCKET", "TADPOLE_BUCKET", "GOAT_HORN", "SCULK_CATALYST", "ENDER_CHEST");
        fix(20, Section.MISCELLANEOUS, "ENDER_EYE", "DISC_FRAGMENT_5", "SPONGE", "SCULK_SHRIEKER", "CRYING_OBSIDIAN");
        fix(15, Section.MISCELLANEOUS, "WET_SPONGE", "BEE_NEST", "SCULK_SENSOR", "SHEARS", "HONEY_BLOCK");
        fix(12, Section.MISCELLANEOUS, "SPYGLASS", "BEEHIVE", "CROSSBOW", "HONEYCOMB_BLOCK", "CHIPPED_ANVIL");
        fix(10, Section.MISCELLANEOUS, "EXPERIENCE_BOTTLE", "SADDLE", "NAME_TAG", "CLOCK", "FLINT_AND_STEEL", "BUNDLE");
        fix(8, Section.MISCELLANEOUS, "COMPASS", "NOTE_BLOCK", "BREWING_STAND", "SMOKER", "HONEY_BOTTLE");
        fix(6, Section.MISCELLANEOUS, "BOW", "SHIELD", "CAULDRON", "LECTERN", "DAMAGED_ANVIL");
        fix(5, Section.MISCELLANEOUS, "TRAPPED_CHEST", "WRITABLE_BOOK", "BRUSH", "POTION", "SPLASH_POTION", "TIPPED_ARROW",
                "CARROT_ON_A_STICK", "WARPED_FUNGUS_ON_A_STICK");
        fix(8, Section.MISCELLANEOUS, "LINGERING_POTION");
        fix(4, Section.MISCELLANEOUS, "SMITHING_TABLE", "GRINDSTONE", "LEAD");
        fix(3, Section.MISCELLANEOUS, "WIND_CHARGE", "FISHING_ROD", "BOOK", "LOOM", "CARTOGRAPHY_TABLE", "FLETCHING_TABLE",
                "BARREL", "CHEST", "HONEYCOMB", "FIREWORK_ROCKET", "FIREWORK_STAR", "SPECTRAL_ARROW");
        fix(2, Section.MISCELLANEOUS, "CRAFTING_TABLE", "FURNACE", "MAP");

        // an ingot sells for 7 and makes 9 nuggets, which could not be priced below 9 at the 1-strata floor, so copper
        // nuggets are not sellable; the many-per-ingot copper items sit at the floor
        fix(1, Section.BUILDING_BLOCKS, "COPPER_BARS", "COPPER_TORCH");
        fix(2, Section.BUILDING_BLOCKS, "COPPER_CHAIN");

        fix(25, Section.REDSTONE_MECHANICS, "COMPARATOR", "POWERED_RAIL");
        fix(22, Section.REDSTONE_MECHANICS, "REDSTONE_LAMP");
        fix(20, Section.REDSTONE_MECHANICS, "HOPPER", "LIGHTNING_ROD");
        fix(18, Section.REDSTONE_MECHANICS, "DAYLIGHT_DETECTOR");
        fix(14, Section.REDSTONE_MECHANICS, "TARGET");
        fix(12, Section.REDSTONE_MECHANICS, "REPEATER", "STICKY_PISTON", "TNT", "DETECTOR_RAIL", "ACTIVATOR_RAIL");
        fix(10, Section.REDSTONE_MECHANICS, "REDSTONE_TORCH");
        fix(8, Section.REDSTONE_MECHANICS, "DISPENSER", "OBSERVER");
        fix(6, Section.REDSTONE_MECHANICS, "DROPPER", "PISTON");
        fix(3, Section.REDSTONE_MECHANICS, "RAIL", "TRIPWIRE_HOOK");

        fix(100, Section.MOB_DROPS, "DRAGON_HEAD");
        fix(25, Section.MOB_DROPS, "CREEPER_HEAD", "PIGLIN_HEAD");
        fix(20, Section.MOB_DROPS, "ZOMBIE_HEAD", "SKELETON_SKULL", "TURTLE_EGG");
        // crafted from priced ingredients, so kept below what those ingredients sell for (hay: 9 wheat = 18)
        fix(15, Section.FARMING_DROPS, "HAY_BLOCK");
        fix(9, Section.FARMING_DROPS, "GOLDEN_CARROT");
        fix(5, Section.MOB_DROPS, "PLAYER_HEAD");

        fix(50, Section.FARMING_DROPS, "WITHER_ROSE");
        fix(8, Section.FARMING_DROPS, "GLISTERING_MELON_SLICE");
        fix(15, Section.FARMING_DROPS, "CAKE");
        fix(10, Section.FARMING_DROPS, "RABBIT_STEW", "SUSPICIOUS_STEW");
        fix(9, Section.FARMING_DROPS, "DRIED_KELP_BLOCK");
        fix(6, Section.FARMING_DROPS, "PUMPKIN_PIE", "BEETROOT_SOUP", "MUSHROOM_STEW", "PITCHER_PLANT");
        fix(5, Section.FARMING_DROPS, "SPORE_BLOSSOM", "TORCHFLOWER", "FROGSPAWN");
        fix(3, Section.FARMING_DROPS, "BEEF", "PORKCHOP", "CHICKEN", "MUTTON", "RABBIT", "BREAD", "BAKED_POTATO",
                "CHORUS_FRUIT", "POPPED_CHORUS_FRUIT", "SEA_PICKLE", "BIG_DRIPLEAF", "AZALEA", "PITCHER_POD", "TORCHFLOWER_SEEDS");
        fix(1, Section.FARMING_DROPS, "COOKIE", "DRIED_KELP", "POISONOUS_POTATO", "WHEAT_SEEDS", "BEETROOT_SEEDS",
                "MELON_SEEDS", "PUMPKIN_SEEDS", "BONE_MEAL");
    }

    private DefaultSellPrices() {}

    /** True for items that can never be sold. */
    public static boolean isNever(String name) {
        String n = name.toUpperCase(Locale.ROOT);
        return NEVER.contains(n) || n.startsWith("LEGACY_") || n.endsWith("_SPAWN_EGG") || n.endsWith("_AIR")
                || isWoodCrafted(n);
    }

    /**
     * Items crafted out of planks (stairs, slabs, fences, doors, signs, boats...) plus sticks, bowls and ladders are
     * not sellable by default: one log makes several of them, so any price would be an arbitrage on the log price.
     * Price one in a section file to bring it back; that always wins.
     */
    public static boolean isWoodCrafted(String name) {
        String n = name.toUpperCase(Locale.ROOT);
        if (n.equals("STICK") || n.equals("BOWL") || n.equals("LADDER") || n.equals("BAMBOO_MOSAIC")) {
            return true;
        }
        for (String wood : WOODS) {
            if (!n.startsWith(wood + "_")) continue;
            for (String suffix : WOOD_CRAFTED) {
                if (n.equals(wood + suffix)) return true;
            }
            if (n.equals(wood + "_MOSAIC_STAIRS") || n.equals(wood + "_MOSAIC_SLAB")) return true;
        }
        return false;
    }

    /** The sell price and category for an item, or null if it must not be sold. */
    public static Entry of(String materialName) {
        String n = materialName.toUpperCase(Locale.ROOT);
        if (isNever(n)) return null;
        Entry fixed = FIXED.get(n);
        if (fixed != null) return fixed;

        // tools, weapons and armour by tier
        for (String piece : PIECES) {
            if (n.endsWith(piece)) {
                String tier = n.substring(0, n.length() - piece.length());
                if (TIERS.containsKey(tier)) return new Entry(gearPrice(tier, piece), Section.MISCELLANEOUS);
            }
        }
        if (n.endsWith("_HORSE_ARMOR")) {
            int price = switch (n.substring(0, n.length() - "_HORSE_ARMOR".length())) {
                case "LEATHER" -> 8;
                case "DIAMOND" -> 40;
                default -> 15;
            };
            return new Entry(price, Section.MISCELLANEOUS);
        }
        if (n.startsWith("COOKED_")) return new Entry(5, Section.FARMING_DROPS);
        if (n.startsWith("MUSIC_DISC_")) return new Entry(40, Section.MISCELLANEOUS);
        if (n.endsWith("_SMITHING_TEMPLATE")) {
            return new Entry(n.startsWith("NETHERITE_UPGRADE") ? 200 : 150, Section.MISCELLANEOUS);
        }
        if (n.endsWith("_BANNER_PATTERN") || n.endsWith("_POTTERY_SHERD")) return new Entry(10, Section.MISCELLANEOUS);
        if (n.endsWith("SHULKER_BOX")) return new Entry(100, Section.MISCELLANEOUS);
        if (n.endsWith("_BED")) return new Entry(6, Section.MISCELLANEOUS);
        if (n.endsWith("_BANNER")) return new Entry(5, Section.MISCELLANEOUS);
        if (n.endsWith("_BOAT") || n.endsWith("_RAFT")) return new Entry(n.contains("CHEST") ? 6 : 3, Section.MISCELLANEOUS);
        if (n.endsWith("_SIGN") || n.endsWith("_CANDLE")) return new Entry(2, Section.MISCELLANEOUS);
        if (n.endsWith("_DYE")) return new Entry(2, Section.FARMING_DROPS);
        if (n.endsWith("_SAPLING") || n.endsWith("_LEAVES") || n.endsWith("_PETALS") || n.endsWith("_SEEDS")) {
            return new Entry(n.endsWith("_SAPLING") ? 2 : 1, Section.FARMING_DROPS);
        }
        if (n.contains("CORAL") || n.endsWith("_FUNGUS") || n.endsWith("MUSHROOM") || n.endsWith("_ROOTS")
                || n.endsWith("_VINES") || n.endsWith("_TULIP") || n.endsWith("_ORCHID") || n.endsWith("_BUSH")) {
            return new Entry(n.contains("CORAL") ? 3 : 1, Section.FARMING_DROPS);
        }
        if (n.contains("COPPER") && !n.contains("ORE") && !n.startsWith("RAW_")) {
            return new Entry(6, Section.BUILDING_BLOCKS); // below the 7 an ingot sells for
        }
        return new Entry(FALLBACK_PRICE, Section.BUILDING_BLOCKS);
    }

    /** 90% of what the ingredients sell for, so crafting gear and selling it never makes money. Minimum 1. */
    public static int gearPrice(String tier, String piece) {
        int count = PIECE_COUNT.get(piece);
        int ingredients = count * TIERS.get(tier);
        if (tier.equals("NETHERITE")) {
            ingredients += NETHERITE_INGOT_VALUE; // the diamond gear it is upgraded from plus one ingot
        }
        return Math.max(1, (int) Math.floor(ingredients * 0.9));
    }

    /** Names the fixed table prices; used by tests to catch typos. */
    static Set<String> fixedNames() {
        return FIXED.keySet();
    }
}
