package com.stratasmp.stratakeystones;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/** What a cleared keystone pays out: Stratas that grow with the level plus rolled item drops. */
public final class KeystoneRewards {
    public static final long DEFAULT_BASE = 50;
    public static final long DEFAULT_PER_LEVEL = 50;

    /** One possible drop. {@code chance} is 0-1. */
    public record Drop(Material material, int min, int max, double chance) {}

    public record Stack(Material material, int amount) {
        public ItemStack toItem() {
            return new ItemStack(material, amount);
        }
    }

    public record Payout(long stratas, List<Stack> items) {}

    private final long base;
    private final long perLevel;
    private final double partyBonusPerExtraMember;
    private final ConfigurationSection items;
    private final Consumer<String> warn;
    private final java.util.function.ToIntFunction<Material> stackSize;

    public KeystoneRewards(ConfigurationSection root, Consumer<String> warn) {
        this(root, warn, Material::getMaxStackSize);
    }

    /** {@code stackSize} is injectable because Material.getMaxStackSize() needs a running server. */
    KeystoneRewards(ConfigurationSection root, Consumer<String> warn, java.util.function.ToIntFunction<Material> stackSize) {
        this.warn = warn;
        this.stackSize = stackSize;
        ConfigurationSection loot = root == null ? null : root.getConfigurationSection("loot");
        this.base = loot == null ? DEFAULT_BASE : Math.max(0, loot.getLong("stratas.base", DEFAULT_BASE));
        this.perLevel = loot == null ? DEFAULT_PER_LEVEL : Math.max(0, loot.getLong("stratas.per-level", DEFAULT_PER_LEVEL));
        this.partyBonusPerExtraMember = loot == null ? 0 : Math.max(0, loot.getDouble("stratas.party-bonus-per-extra-member", 0));
        this.items = loot == null ? null : loot.getConfigurationSection("items");
    }

    /** Stratas for clearing {@code level}, with an optional bonus per extra party member. Never negative or overflowing. */
    public long stratasFor(int level, int partySize) {
        long steps = Math.max(0, level - 1);
        long amount = base + perLevel * steps;
        int extra = Math.max(0, partySize - 1);
        if (extra > 0 && partyBonusPerExtraMember > 0) {
            amount = (long) Math.floor(amount * (1.0 + partyBonusPerExtraMember * extra));
        }
        return Math.max(0, amount);
    }

    public List<Drop> dropsFor(int level) {
        List<Drop> drops = new ArrayList<>();
        if (items == null) return drops;
        for (String spec : items.getStringList(String.valueOf(level))) {
            Drop drop = parse(spec);
            if (drop == null) {
                warn.accept("Bad keystone loot entry for level " + level + ": \"" + spec + "\" (use MATERIAL min[-max] [chance%])");
            } else {
                drops.add(drop);
            }
        }
        return drops;
    }

    /** Rolls one player's share. Each participant rolls separately. */
    public Payout roll(int level, int partySize, Random random) {
        return new Payout(stratasFor(level, partySize), rollDrops(dropsFor(level), random, stackSize));
    }

    /** Rolls a list of drops into legal stacks. Shared with other modules that pay out items. */
    public static List<Stack> rollDrops(List<Drop> drops, Random random, java.util.function.ToIntFunction<Material> stackSize) {
        List<Stack> stacks = new ArrayList<>();
        for (Drop drop : drops) {
            if (random.nextDouble() >= drop.chance()) continue;
            int amount = drop.min() == drop.max() ? drop.min() : drop.min() + random.nextInt(drop.max() - drop.min() + 1);
            if (amount <= 0) continue;
            int stack = Math.max(1, stackSize.applyAsInt(drop.material()));
            while (amount > 0) {
                int part = Math.min(amount, stack);
                stacks.add(new Stack(drop.material(), part));
                amount -= part;
            }
        }
        return stacks;
    }

    /** "DIAMOND 1-3 25%" - amount is a number or a range, the chance is optional and defaults to 100%. */
    public static Drop parse(String spec) {
        if (spec == null) return null;
        String[] parts = spec.trim().split("\\s+");
        if (parts.length < 2 || parts.length > 3) return null;
        Material material = Material.getMaterial(parts[0].toUpperCase(Locale.ROOT));
        if (material == null || !isItemName(material)) return null;
        int min;
        int max;
        try {
            String[] range = parts[1].split("-");
            if (range.length > 2) return null;
            min = Integer.parseInt(range[0]);
            max = range.length == 2 ? Integer.parseInt(range[1]) : min;
        } catch (NumberFormatException e) {
            return null;
        }
        if (min <= 0 || max < min || max > 2304) return null;
        double chance = 1.0;
        if (parts.length == 3) {
            String raw = parts[2].endsWith("%") ? parts[2].substring(0, parts[2].length() - 1) : parts[2];
            try {
                chance = Double.parseDouble(raw) / 100.0;
            } catch (NumberFormatException e) {
                return null;
            }
            if (!(chance > 0 && chance <= 1.0)) return null;
        }
        return new Drop(material, min, max, chance);
    }

    /** Material.isItem() needs a running server, so legacy and block-only names are screened by name instead. */
    private static boolean isItemName(Material material) {
        String name = material.name();
        return !name.startsWith("LEGACY_") && !name.endsWith("_AIR") && !name.equals("AIR") && !name.equals("WATER") && !name.equals("LAVA")
                && !name.equals("FIRE") && !name.equals("BEDROCK") && !name.endsWith("_WALL_SIGN") && !name.endsWith("_WALL_HEAD");
    }
}
