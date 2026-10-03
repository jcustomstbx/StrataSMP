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
    private static final Map<String, Integer> TIERS = new HashMap<>();
    private static final String[] PIECES = {
            "_SWORD", "_PICKAXE", "_AXE", "_SHOVEL", "_HOE", "_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS", "_SPEAR"};
    private static final Set<String> NEVER = new HashSet<>();

    private static void fix(int price, Section section, String... names) {
        for (String name : names) FIXED.put(name, new Entry(price, section));
    }

    static {
        // creative-only, technical or unobtainable items are never sellable
        for (String n : new String[] {
                "AIR", "CAVE_AIR", "VOID_AIR", "BARRIER", "BEDROCK", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK",
                "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART", "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "LIGHT",
                "DEBUG_STICK", "KNOWLEDGE_BOOK", "SPAWNER", "TRIAL_SPAWNER", "VAULT", "REINFORCED_DEEPSLATE",
                "END_PORTAL_FRAME", "BUDDING_AMETHYST", "DRAGON_EGG", "FARMLAND", "PETRIFIED_OAK_SLAB", "WRITTEN_BOOK",
                "FILLED_MAP", "TEST_BLOCK", "TEST_INSTANCE_BLOCK", "CHORUS_PLANT"}) {
            NEVER.add(n);
        }

        TIERS.put("WOODEN", 2);
        TIERS.put("STONE", 2);
        TIERS.put("LEATHER", 8);
        TIERS.put("CHAINMAIL", 12);
        TIERS.put("IRON", 20);
        TIERS.put("GOLDEN", 20);
        TIERS.put("COPPER", 14);
        TIERS.put("DIAMOND", 80);
        TIERS.put("NETHERITE", 220);

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
        fix(20, Section.MOB_DROPS, "ZOMBIE_HEAD", "SKELETON_SKULL", "GOLDEN_CARROT", "HAY_BLOCK", "TURTLE_EGG");
        fix(5, Section.MOB_DROPS, "PLAYER_HEAD");

        fix(50, Section.FARMING_DROPS, "WITHER_ROSE");
        fix(18, Section.FARMING_DROPS, "GLISTERING_MELON_SLICE");
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
        return NEVER.contains(n) || n.startsWith("LEGACY_") || n.endsWith("_SPAWN_EGG") || n.endsWith("_AIR");
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
                Integer price = TIERS.get(tier);
                if (price != null) return new Entry(price, Section.MISCELLANEOUS);
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
            return new Entry(8, Section.BUILDING_BLOCKS);
        }
        return new Entry(FALLBACK_PRICE, Section.BUILDING_BLOCKS);
    }

    /** Names the fixed table prices; used by tests to catch typos. */
    static Set<String> fixedNames() {
        return FIXED.keySet();
    }
}
