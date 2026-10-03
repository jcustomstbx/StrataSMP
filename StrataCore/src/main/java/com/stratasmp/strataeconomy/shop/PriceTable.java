package com.stratasmp.strataeconomy.shop;

import com.stratasmp.strataeconomy.api.Prices;
import com.stratasmp.strataeconomy.StrataEconomy;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Prices are catalogued per {@link Section} under {@code sections/<id>.yml}
 * rather than one flat list, so /shop and /sell can group items and a whole
 * category's sell/buy side can be switched off without touching every price.
 */
public final class PriceTable implements Prices {

    private static final String[] COLOURS = {
            "WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY",
            "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"
    };
    private static final String[] COLOURED_SUFFIXES = {
            "_WOOL", "_CONCRETE", "_CONCRETE_POWDER", "_TERRACOTTA",
            "_GLAZED_TERRACOTTA", "_STAINED_GLASS", "_STAINED_GLASS_PANE", "_CANDLE"
    };

    private final StrataEconomy plugin;
    private final Map<Material, Integer> sell = new EnumMap<>(Material.class);
    private final Map<Material, Integer> buy = new EnumMap<>(Material.class);
    private final Map<Material, Section> sellSection = new EnumMap<>(Material.class);
    private final Map<Material, Section> buySection = new EnumMap<>(Material.class);
    private final Map<Material, Integer> dailyBuyLimit = new EnumMap<>(Material.class);
    private final File stateFile;
    private volatile double multiplier = 1.0;

    private final java.util.function.Predicate<Material> itemCheck;

    public PriceTable(StrataEconomy plugin) {
        this(plugin, PriceTable::isObtainableItem);
    }

    /** The item check is a parameter so tests can run without a server (Material#isItem needs one). */
    public PriceTable(StrataEconomy plugin, java.util.function.Predicate<Material> itemCheck) {
        this.plugin = plugin;
        this.itemCheck = itemCheck;
        this.stateFile = new File(plugin.getDataFolder(), "prices-state.yml");
        reload();
        loadState();
    }

    public void reload() {
        sell.clear();
        buy.clear();
        sellSection.clear();
        buySection.clear();
        dailyBuyLimit.clear();
        for (Section section : Section.values()) {
            loadSection(section);
        }
        fillDefaultSellPrices();
        plugin.getLogger().info("Prices: " + sell.size() + " sellable, " + buy.size() + " buyable materials.");
    }

    private static boolean isObtainableItem(Material m) {
        try {
            return m.isItem() && !m.isLegacy();
        } catch (Throwable noServer) {
            return false;
        }
    }

    /**
     * Every item that has no price in a section file gets a default sell price, so anything a player can hold can be
     * sold. shop.sell-everything turns this off; shop.unsellable removes individual items, whoever priced them.
     */
    private void fillDefaultSellPrices() {
        java.util.Set<Material> blocked = new java.util.HashSet<>();
        for (String name : plugin.getConfig().getStringList("shop.unsellable")) {
            Material m = Material.matchMaterial(name);
            if (m == null) {
                plugin.getLogger().warning("Unknown material in shop.unsellable: " + name);
            } else {
                blocked.add(m);
            }
        }
        if (plugin.getConfig().getBoolean("shop.sell-everything", true)) {
            for (Material m : Material.values()) {
                if (sell.containsKey(m) || blocked.contains(m) || !itemCheck.test(m)) {
                    continue;
                }
                DefaultSellPrices.Entry entry = DefaultSellPrices.of(m.name());
                if (entry != null && entry.section().sellable) {
                    sell.put(m, entry.price());
                    sellSection.put(m, entry.section());
                }
            }
        }
        for (Material m : blocked) {
            sell.remove(m);
            sellSection.remove(m);
        }
    }

    private void loadSection(Section section) {
        String resourcePath = "sections/" + section.id + ".yml";
        File f = new File(plugin.getDataFolder(), resourcePath);
        if (!f.exists()) {
            plugin.saveResource(resourcePath, false);
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);

        ConfigurationSection items = y.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                if (m == null) {
                    plugin.getLogger().warning("Unknown material in " + resourcePath + ": " + key);
                    continue;
                }
                ConfigurationSection entry = items.getConfigurationSection(key);
                if (section.sellable && entry.contains("sell")) {
                    sell.put(m, Math.max(0, entry.getInt("sell")));
                    sellSection.put(m, section);
                }
                if (section.buyable && entry.contains("buy")) {
                    buy.put(m, Math.max(0, entry.getInt("buy")));
                    buySection.put(m, section);
                    if (entry.contains("daily-limit")) {
                        dailyBuyLimit.put(m, Math.max(0, entry.getInt("daily-limit")));
                    }
                }
            }
        }

        if (section == Section.BUILDING_BLOCKS) {
            int coloredBuy = y.getInt("colored-buy", 60);
            int coloredSell = y.getInt("colored-sell", 10);
            for (String colour : COLOURS) {
                for (String suffix : COLOURED_SUFFIXES) {
                    Material m = Material.matchMaterial(colour + suffix);
                    if (m == null) {
                        continue;
                    }
                    if (section.buyable) {
                        buy.putIfAbsent(m, coloredBuy);
                        buySection.putIfAbsent(m, section);
                    }
                    if (section.sellable) {
                        sell.putIfAbsent(m, coloredSell);
                        sellSection.putIfAbsent(m, section);
                    }
                }
            }
        }
    }

    private void loadState() {
        if (stateFile.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(stateFile);
            multiplier = clamp(y.getDouble("multiplier", 1.0));
        }
    }

    private void saveState() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("multiplier", multiplier);
        try {
            y.save(stateFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save prices-state.yml: " + e.getMessage());
        }
    }

    private static double clamp(double v) {
        return Math.max(0.5, Math.min(2.0, v));
    }

    @Override public int baseSellPrice(Material m) { return sell.getOrDefault(m, 0); }
    @Override public int baseBuyPrice(Material m) { return buy.getOrDefault(m, 0); }

    @Override public int sellPrice(Material m) {
        int base = baseSellPrice(m);
        return base == 0 ? 0 : Math.max(1, (int) Math.round(base * multiplier));
    }

    @Override public int buyPrice(Material m) {
        int base = baseBuyPrice(m);
        return base == 0 ? 0 : Math.max(1, (int) Math.round(base * multiplier));
    }

    @Override public boolean canSell(Material m) { return sell.containsKey(m); }
    @Override public boolean canBuy(Material m) { return buy.containsKey(m); }

    @Override public double getGlobalMultiplier() { return multiplier; }

    @Override public void setGlobalMultiplier(double m) {
        this.multiplier = clamp(m);
        saveState();
    }

    public Map<Material, Integer> sellView() { return java.util.Collections.unmodifiableMap(sell); }
    public Map<Material, Integer> buyView() { return java.util.Collections.unmodifiableMap(buy); }

    /** 0 = unlimited. */
    public int dailyBuyLimit(Material m) { return dailyBuyLimit.getOrDefault(m, 0); }

    /* ---- section-aware views, for the GUIs ---- */

    public Section sectionOf(Material m, boolean forBuying) {
        return forBuying ? buySection.get(m) : sellSection.get(m);
    }

    public List<Material> buyableIn(Section section) {
        List<Material> out = new ArrayList<>();
        for (var e : buySection.entrySet()) {
            if (e.getValue() == section) {
                out.add(e.getKey());
            }
        }
        out.sort(Comparator.comparing(Enum::name));
        return out;
    }

    public List<Material> sellableIn(Section section) {
        List<Material> out = new ArrayList<>();
        for (var e : sellSection.entrySet()) {
            if (e.getValue() == section) {
                out.add(e.getKey());
            }
        }
        out.sort(Comparator.comparing(Enum::name));
        return out;
    }

    /** Sections with at least one sellable item, in catalog order. */
    public List<Section> activeSellSections() {
        List<Section> out = new ArrayList<>();
        for (Section section : Section.values()) {
            if (section.sellable && !sellableIn(section).isEmpty()) {
                out.add(section);
            }
        }
        return out;
    }

    /** Sections with at least one buyable item, in catalog order. */
    public List<Section> activeBuySections() {
        List<Section> out = new ArrayList<>();
        for (Section section : Section.values()) {
            if (section.buyable && !buyableIn(section).isEmpty()) {
                out.add(section);
            }
        }
        return out;
    }
}
