package com.stratasmp.strataeconomy.shop;

import com.stratasmp.strataeconomy.StrataEconomy;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Console record of /sell activity, for spotting duplication: a sale is logged when it pays out a lot or moves a lot
 * of one item, and a player whose sales add up to a large sum within an hour gets a warning line. Limits live under
 * {@code shop.sell-log} in config.yml. Main thread only.
 */
public final class SaleLog {

    private static final long HOUR_MILLIS = 3_600_000L;
    private static final int HISTORY_CAP = 100;

    /** One flagged sale, for /strataeconomy saleslog - the same information the console line carries. */
    public record Sale(long whenMillis, String player, String items, long payout, String world, int x, int y, int z) {
    }

    private final StrataEconomy plugin;
    private final Map<UUID, ArrayDeque<long[]>> recent = new HashMap<>();
    private final Map<UUID, Long> lastWarned = new HashMap<>();
    private final ArrayDeque<Sale> history = new ArrayDeque<>();

    public SaleLog(StrataEconomy plugin) {
        this.plugin = plugin;
    }

    public void record(Player player, Map<Material, Integer> sold, long payout) {
        long minStratas = plugin.getConfig().getLong("shop.sell-log.min-stratas", 1000L);
        int minItems = plugin.getConfig().getInt("shop.sell-log.min-items", 640);
        int biggestStack = sold.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (payout >= minStratas || biggestStack >= minItems) {
            String items = sold.entrySet().stream()
                    .sorted(Map.Entry.<Material, Integer>comparingByValue().reversed())
                    .map(e -> e.getValue() + "x " + e.getKey().name().toLowerCase())
                    .collect(Collectors.joining(", "));
            plugin.getLogger().info("[Sell] " + player.getName() + " sold " + items + " for " + payout + " Stratas ("
                    + player.getWorld().getName() + " " + player.getLocation().getBlockX() + ","
                    + player.getLocation().getBlockY() + "," + player.getLocation().getBlockZ() + ")");
            history.addLast(new Sale(System.currentTimeMillis(), player.getName(), items, payout,
                    player.getWorld().getName(), player.getLocation().getBlockX(),
                    player.getLocation().getBlockY(), player.getLocation().getBlockZ()));
            while (history.size() > HISTORY_CAP) {
                history.removeFirst();
            }
        }
        warnIfHeavy(player, payout);
    }

    /** Flagged sales this run, oldest first - not persisted, so it's empty again after a restart. */
    public java.util.List<Sale> history() {
        return new java.util.ArrayList<>(history);
    }

    private void warnIfHeavy(Player player, long payout) {
        long now = System.currentTimeMillis();
        ArrayDeque<long[]> sales = recent.computeIfAbsent(player.getUniqueId(), id -> new ArrayDeque<>());
        sales.addLast(new long[]{now, payout});
        while (!sales.isEmpty() && now - sales.peekFirst()[0] > HOUR_MILLIS) {
            sales.removeFirst();
        }
        long hourly = plugin.getConfig().getLong("shop.sell-log.hourly-warn-stratas", 20_000L);
        long total = sales.stream().mapToLong(s -> s[1]).sum();
        Long warned = lastWarned.get(player.getUniqueId());
        if (hourly > 0 && total >= hourly && (warned == null || now - warned > HOUR_MILLIS)) {
            lastWarned.put(player.getUniqueId(), now);
            plugin.getLogger().warning("[Sell] " + player.getName() + " has sold " + total + " Stratas worth of items in the last hour.");
        }
    }
}
