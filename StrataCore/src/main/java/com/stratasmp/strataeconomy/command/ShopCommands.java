package com.stratasmp.strataeconomy.command;

import com.stratasmp.strataeconomy.StrataEconomy;
import com.stratasmp.strataeconomy.api.Auctions;
import com.stratasmp.strataeconomy.api.BuyOrders;
import com.stratasmp.strataeconomy.auction.AuctionStore;
import com.stratasmp.strataeconomy.auction.BuyOrderStore;
import com.stratasmp.strataeconomy.currency.Amounts;
import com.stratasmp.strataeconomy.gui.AuctionGui;
import com.stratasmp.strataeconomy.gui.BuyOrderGui;
import com.stratasmp.strataeconomy.gui.SellGui;
import com.stratasmp.strataeconomy.gui.SellablesGui;
import com.stratasmp.strataeconomy.gui.ShopGui;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Text driver for /ah, /sell and /shop. GUIs are layered on top later; the
 * commands stay as the fallback / power-user path.
 */
public final class ShopCommands implements CommandExecutor {

    private final StrataEconomy plugin;
    private final AuctionGui auctionGui;
    private final BuyOrderGui buyOrderGui;
    private final SellGui sellGui;
    private final ShopGui shopGui;
    private final SellablesGui sellablesGui;

    public ShopCommands(StrataEconomy plugin, AuctionGui auctionGui, BuyOrderGui buyOrderGui, SellGui sellGui, ShopGui shopGui,
                        SellablesGui sellablesGui) {
        this.plugin = plugin;
        this.auctionGui = auctionGui;
        this.buyOrderGui = buyOrderGui;
        this.sellGui = sellGui;
        this.shopGui = shopGui;
        this.sellablesGui = sellablesGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "players-only");
            return true;
        }
        String commandName = command.getName().toLowerCase(java.util.Locale.ROOT);
        if ((commandName.equals("shop") || commandName.equals("sell") || commandName.equals("sellall") || commandName.equals("ah"))
                && com.stratasmp.strataeconomy.gui.Gui.isRestrictedWorld(plugin, player)) {
            player.sendMessage(plugin.msg().plain("menu-restricted-world"));
            return true;
        }
        switch (command.getName().toLowerCase()) {
            case "sell" -> sell(player, args);
            case "sellall" -> {
                if (com.stratasmp.strataranks.RankTier.of(player).ordinal() < com.stratasmp.strataranks.RankTier.ORNATE.ordinal()) {
                    player.sendMessage("/sellall requires Ornate or Regal.");
                } else if (!java.util.Set.of("world", "world_nether", "world_the_end").contains(player.getWorld().getName())) {
                    player.sendMessage("/sellall is available in the SMP worlds.");
                } else {
                    sellGui.sellAll(player);
                }
            }
            case "shop" -> shop(player, args);
            case "ah" -> auction(player, args);
            default -> { }
        }
        return true;
    }

    /* ---- /sell ---- */

    private void sell(Player p, String[] args) {
        if (args.length == 0) {
            sellGui.open(p);
            return;
        }
        boolean handOnly = args.length >= 1 && args[0].equalsIgnoreCase("hand");
        var prices = plugin.prices();
        long total = 0;
        int count = 0;
        Map<Material, Integer> sold = new java.util.EnumMap<>(Material.class);
        // storage slots only: offhand and armour are never sold (the sell GUI excludes them too)
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType().isAir()) {
                continue;
            }
            if (handOnly && i != p.getInventory().getHeldItemSlot()) {
                continue;
            }
            if (it.hasItemMeta() && it.getItemMeta().hasCustomModelData()) {
                continue; // never buy custom-model items (weapons/skins)
            }
            int unit = prices.sellPrice(it.getType());
            if (unit <= 0) {
                continue;
            }
            total += (long) unit * it.getAmount();
            count += it.getAmount();
            sold.merge(it.getType(), it.getAmount(), Integer::sum);
            p.getInventory().setItem(i, null);
        }
        if (count == 0) {
            plugin.msg().send(p, "sell-none");
            return;
        }
        double tax = plugin.getConfig().getDouble("shop.sell-tax-percent", 0.0);
        if (tax > 0) {
            total -= (long) Math.floor(total * tax / 100.0);
        }
        plugin.stratas().deposit(p.getUniqueId(), total);
        plugin.saleLog().record(p, sold, total);
        plugin.msg().send(p, "sell-done", Map.of("count", String.valueOf(count), "amount", plugin.money(total)));
    }

    /* ---- /shop ---- */

    private void shop(Player p, String[] args) {
        if (args.length == 0) {
            shopGui.openLanding(p);
            return;
        }
        if (args[0].toLowerCase().startsWith("sellable")) {
            sellablesGui.openLanding(p);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("buy") && args.length >= 2) {
            Material m = Material.matchMaterial(args[1].toUpperCase());
            if (m == null || !plugin.prices().canBuy(m)) {
                p.sendMessage("Not for sale: " + args[1]);
                return;
            }
            int requested = 1;
            if (args.length >= 3) {
                try {
                    requested = Math.max(1, Math.min(2304, Integer.parseInt(args[2])));
                } catch (NumberFormatException ignored) {
                }
            }
            int cap = ((com.stratasmp.strataeconomy.shop.PriceTable) plugin.prices()).dailyBuyLimit(m);
            int remaining = plugin.purchaseLimits().remaining(p.getUniqueId(), m, cap);
            if (remaining <= 0) {
                plugin.msg().send(p, "shop-daily-limit", Map.of("item", m.name().toLowerCase().replace('_', ' ')));
                return;
            }
            int amount = Math.min(requested, remaining);
            long cost = (long) plugin.prices().buyPrice(m) * amount;
            if (!plugin.stratas().has(p.getUniqueId(), cost)) {
                plugin.msg().send(p, "shop-cant-afford");
                return;
            }
            if (p.getInventory().firstEmpty() == -1) {
                plugin.msg().send(p, "shop-inventory-full");
                return;
            }
            plugin.stratas().withdraw(p.getUniqueId(), cost);
            int toGive = amount;
            int leftover = 0;
            while (toGive > 0) {
                int stack = Math.min(toGive, m.getMaxStackSize());
                leftover += p.getInventory().addItem(new ItemStack(m, stack)).values().stream().mapToInt(ItemStack::getAmount).sum();
                toGive -= stack;
            }
            if (leftover > 0) {
                // whatever didn't fit is refunded rather than paid for and lost
                long refund = (long) plugin.prices().buyPrice(m) * leftover;
                plugin.stratas().deposit(p.getUniqueId(), refund);
                amount -= leftover;
                cost -= refund;
            }
            plugin.purchaseLimits().record(p.getUniqueId(), m, amount);
            plugin.msg().send(p, amount < requested ? "shop-bought-limited" : "shop-bought", Map.of(
                    "item", amount + "x " + m.name().toLowerCase().replace('_', ' '),
                    "price", plugin.money(cost)));
            return;
        }
        p.sendMessage("§6Shop §7- §f/shop buy <material> [amount] §7- §f/shop sellables §7(what you can sell)");
        p.sendMessage("§7Price multiplier: §e" + plugin.prices().getGlobalMultiplier());
        int shown = 0;
        for (var e : ((com.stratasmp.strataeconomy.shop.PriceTable) plugin.prices()).buyView().entrySet()) {
            if (shown++ >= 20) {
                p.sendMessage("§7... and more");
                break;
            }
            p.sendMessage("§7 - §f" + e.getKey().name().toLowerCase() + " §7= §6" + plugin.prices().buyPrice(e.getKey()));
        }
    }

    /* ---- /ah ---- */

    private void auction(Player p, String[] args) {
        Auctions ah = plugin.auctions();
        AuctionStore store = (AuctionStore) ah;

        if (args.length == 0) {
            auctionGui.openBrowse(p, 0);
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "sell" -> {
                if (args.length < 2) {
                    p.sendMessage("/ah sell <price>");
                    return;
                }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    plugin.msg().send(p, "ah-hold-item");
                    return;
                }
                long price;
                try {
                    price = Amounts.parse(args[1]);
                } catch (NumberFormatException e) {
                    plugin.msg().send(p, "invalid-amount", Map.of("amount", args[1]));
                    return;
                }
                if (price < store.minPrice() || price > store.maxPrice()) {
                    plugin.msg().send(p, "ah-bad-price", Map.of(
                            "min", String.valueOf(store.minPrice()), "max", String.valueOf(store.maxPrice())));
                    return;
                }
                if (!p.hasPermission("strataeconomy.admin")
                        && ah.bySeller(p.getUniqueId()).size() >= store.maxPerPlayer(p)) {
                    plugin.msg().send(p, "ah-max-listings", Map.of("amount", String.valueOf(store.maxPerPlayer(p))));
                    return;
                }
                long fee = store.listingFee(price);
                if (fee > 0 && !plugin.stratas().withdraw(p.getUniqueId(), fee)) {
                    plugin.msg().send(p, "shop-cant-afford");
                    return;
                }
                ItemStack listed = hand.clone();
                try {
                    ah.list(p, listed, price);
                } catch (IllegalStateException e) {
                    if (fee > 0) {
                        plugin.stratas().deposit(p.getUniqueId(), fee);
                    }
                    plugin.getLogger().severe("Could not persist auction listing: " + e.getMessage());
                    p.sendMessage("The listing could not be saved. Your item was not listed; please try again.");
                    return;
                }
                p.getInventory().setItemInMainHand(null);
                plugin.msg().send(p, "ah-listed", Map.of("item", describe(listed), "price", plugin.money(price)));
                if (fee > 0) {
                    plugin.msg().send(p, "ah-list-fee", Map.of("amount", plugin.money(fee)));
                }
            }
            case "buy" -> {
                Auctions.Listing l = pick(ah.all(), args);
                if (l == null) {
                    p.sendMessage("/ah buy <number>");
                    return;
                }
                String err = ah.buy(p, l.id());
                if (err != null) {
                    p.sendMessage(err);
                } else {
                    plugin.msg().send(p, "ah-bought", Map.of("item", describe(l.item()), "price", plugin.money(l.price())));
                }
            }
            case "mine" -> auctionGui.openMine(p, 0);
            case "cancel" -> {
                Auctions.Listing l = pick(ah.bySeller(p.getUniqueId()), args);
                if (l == null) {
                    p.sendMessage("/ah cancel <number>");
                    return;
                }
                String err = ah.cancel(p, l.id());
                if (err != null) {
                    p.sendMessage(err);
                } else {
                    plugin.msg().send(p, "ah-cancelled");
                }
            }
            case "buyorder" -> buyOrder(p, args);
            case "buyorders" -> buyOrderGui.openBrowse(p, 0);
            case "myorders" -> buyOrderGui.openMine(p, 0);
            case "claim" -> buyOrderGui.openDeliveries(p, 0);
            case "claimitems" -> {
                int claimed = store.claimDeliveries(p);
                p.sendMessage(claimed > 0
                        ? "Claimed " + claimed + " auction item delivery/deliveries."
                        : "No auction items could be claimed. Check your inventory space and try again.");
            }
            default -> p.sendMessage("/ah | /ah sell <price> | /ah buy <n> | /ah mine | /ah cancel <n> | "
                    + "/ah buyorder <price> [amount] | /ah buyorders | /ah myorders | /ah claim | /ah claimitems");
        }
    }

    /* ---- /ah buyorder ---- */

    private void buyOrder(Player p, String[] args) {
        BuyOrders bo = plugin.buyOrders();
        BuyOrderStore store = (BuyOrderStore) bo;

        if (args.length < 2) {
            p.sendMessage("/ah buyorder <price> [amount]");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            plugin.msg().send(p, "ah-hold-item");
            return;
        }
        long price;
        try {
            price = Amounts.parse(args[1]);
        } catch (NumberFormatException e) {
            plugin.msg().send(p, "invalid-amount", Map.of("amount", args[1]));
            return;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                plugin.msg().send(p, "invalid-amount", Map.of("amount", args[2]));
                return;
            }
        }
        if (amount <= 0 || amount > store.maxAmount()) {
            plugin.msg().send(p, "bo-bad-amount");
            return;
        }
        if (price < store.minPrice() || price > store.maxPrice()) {
            plugin.msg().send(p, "bo-bad-price", Map.of(
                    "min", String.valueOf(store.minPrice()), "max", String.valueOf(store.maxPrice())));
            return;
        }
        String err = bo.create(p, hand.getType(), amount, price);
        if (err != null) {
            p.sendMessage(err);
            return;
        }
        plugin.msg().send(p, "bo-created", Map.of(
                "amount", String.valueOf(amount),
                "item", hand.getType().name().toLowerCase().replace('_', ' '),
                "price", plugin.money(price * amount)));
    }

    private Auctions.Listing pick(List<Auctions.Listing> list, String[] args) {
        if (args.length < 2) {
            return null;
        }
        try {
            int idx = Integer.parseInt(args[1]) - 1;
            return idx >= 0 && idx < list.size() ? list.get(idx) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String describe(ItemStack it) {
        int n = it.getAmount();
        String name = it.getType().name().toLowerCase().replace('_', ' ');
        return (n > 1 ? n + "x " : "") + name;
    }
}
