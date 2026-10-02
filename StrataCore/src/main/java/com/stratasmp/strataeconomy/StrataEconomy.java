package com.stratasmp.strataeconomy;

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;

import com.stratasmp.strataeconomy.api.Auctions;
import com.stratasmp.strataeconomy.api.BuyOrders;
import com.stratasmp.strataeconomy.api.StrataApi;
import com.stratasmp.strataeconomy.api.Prices;
import com.stratasmp.strataeconomy.auction.AuctionStore;
import com.stratasmp.strataeconomy.auction.BuyOrderStore;
import com.stratasmp.strataeconomy.command.BalTopCommand;
import com.stratasmp.strataeconomy.command.StratasCommand;
import com.stratasmp.strataeconomy.command.EcoCommand;
import com.stratasmp.strataeconomy.command.PayCommand;
import com.stratasmp.strataeconomy.command.ShopCommands;
import com.stratasmp.strataeconomy.currency.Amounts;
import com.stratasmp.strataeconomy.currency.StratasService;
import com.stratasmp.strataeconomy.currency.Database;
import com.stratasmp.strataeconomy.currency.VaultStratasProvider;
import com.stratasmp.strataeconomy.gui.AuctionGui;
import com.stratasmp.strataeconomy.gui.BuyOrderGui;
import com.stratasmp.strataeconomy.gui.SellGui;
import com.stratasmp.strataeconomy.gui.SellablesGui;
import com.stratasmp.strataeconomy.gui.ShopGui;
import com.stratasmp.strataeconomy.listener.JoinListener;
import com.stratasmp.strataeconomy.migrate.LegacyImport;
import com.stratasmp.strataeconomy.papi.StratasExpansion;
import com.stratasmp.strataeconomy.shop.PriceTable;
import com.stratasmp.strataeconomy.shop.PurchaseLimits;
import com.stratasmp.strataeconomy.shop.SaleLog;
import com.stratasmp.strataeconomy.shop.SpecialOffers;
import com.stratasmp.strataeconomy.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StrataEconomy extends StrataModule implements StrataApi {
   public StrataEconomy(StrataCore core) {
      super(core, "StrataEconomy");
   }


    private Database database;
    private StratasService stratas;
    private PriceTable prices;
    private PurchaseLimits purchaseLimits;
    private SaleLog saleLog;
    private SpecialOffers specialOffers;
    private AuctionStore auctions;
    private BuyOrderStore buyOrders;
    private VaultStratasProvider vault;
    private StratasExpansion stratasExpansion;
    private Msg msg;
    private YamlConfiguration messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("messages.yml");
        this.msg = new Msg(this);
        this.messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));

        try {
            this.database = new Database(getConfig().getConfigurationSection("database"), getLogger());
        } catch (RuntimeException ex) {
            getLogger().severe("Database connection failed - StrataEconomy is disabling. " + ex.getMessage());
            throw new IllegalStateException("StrataEconomy database connection failed; aborting StrataCore startup.", ex);
        }

        long starting = getConfig().getLong("currency.starting-balance", 0);
        this.stratas = new StratasService(this, database, starting);
        this.prices = new PriceTable(this);
        this.purchaseLimits = new PurchaseLimits();
        this.saleLog = new SaleLog(this);
        this.specialOffers = new SpecialOffers(this);
        this.auctions = new AuctionStore(this, database);
        this.buyOrders = new BuyOrderStore(this, database);

        new LegacyImport(this, database).runIfNeeded();

        // Vault provider
        if (getConfig().getBoolean("vault.register", true)
                && getServer().getPluginManager().getPlugin("Vault") != null) {
            this.vault = new VaultStratasProvider(stratas,
                    getConfig().getString("currency.symbol", "♛"),
                    getConfig().getString("currency.name-singular", "Strata"),
                    getConfig().getString("currency.name-plural", "Stratas"));
            getServer().getServicesManager().register(Economy.class, vault, core(), ServicePriority.Highest);
            getLogger().info("Registered as the Vault economy provider.");
        }

        // our own API for StrataElections
        getServer().getServicesManager().register(StrataApi.class, this, core(), ServicePriority.Normal);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            PlaceholderAPIPlugin placeholderAPI = PlaceholderAPIPlugin.getInstance();
            if (placeholderAPI != null) {
                PlaceholderExpansion existing = placeholderAPI.getLocalExpansionManager().getExpansion("stratas");
                if (existing != null && existing.getClass().getName().equals(StratasExpansion.class.getName())) {
                    existing.unregister();
                }
            }
            this.stratasExpansion = new StratasExpansion(this);
            if (this.stratasExpansion.register()) {
                getLogger().info("PlaceholderAPI expansion 'stratas' registered.");
            } else {
                getLogger().warning("Couldn't register PlaceholderAPI expansion 'stratas'.");
            }
        }

        getServer().getPluginManager().registerEvents(new JoinListener(this), this);

        AuctionGui auctionGui = new AuctionGui(this);
        BuyOrderGui buyOrderGui = new BuyOrderGui(this);
        auctionGui.setBuyOrderGui(buyOrderGui);
        buyOrderGui.setAuctionGui(auctionGui);
        SellGui sellGui = new SellGui(this);
        ShopGui shopGui = new ShopGui(this);
        SellablesGui sellablesGui = new SellablesGui(this);
        shopGui.setSellablesGui(sellablesGui);
        getServer().getPluginManager().registerEvents(sellablesGui, this);
        getServer().getPluginManager().registerEvents(auctionGui, this);
        getServer().getPluginManager().registerEvents(buyOrderGui, this);
        getServer().getPluginManager().registerEvents(sellGui, this);
        getServer().getPluginManager().registerEvents(shopGui, this);

        bind("stratas", new StratasCommand(this));
        bind("pay", new PayCommand(this));
        bind("baltop", new BalTopCommand(this));
        bind("eco", new EcoCommand(this));
        ShopCommands shop = new ShopCommands(this, auctionGui, buyOrderGui, sellGui, shopGui, sellablesGui);
        bind("ah", shop);
        bind("sell", shop);
        bind("sellall", shop);
        bind("shop", shop);
        bind("strataeconomy", (sender, command, label, args) -> {
            if (!sender.hasPermission("strataeconomy.admin")) {
                msg.send(sender, "no-permission");
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                prices.reload();
                specialOffers.reload();
                this.messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
                this.msg = new Msg(this);
                msg.send(sender, "reloaded");
                return true;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("saleslog")) {
                sendSalesLog(sender);
                return true;
            }
            sender.sendMessage("/strataeconomy reload | status | saleslog");
            if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
                sender.sendMessage("balances cached: " + top(999999).size()
                        + " | auctions: " + auctions.all().size()
                        + " | price multiplier: " + prices.getGlobalMultiplier());
            }
            return true;
        });

        long autosave = Math.max(20L, getConfig().getLong("autosave-seconds", 60)) * 20L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, stratas::flushNow, autosave, autosave);

        getLogger().info("StrataEconomy enabled.");
    }

    @Override
    public void onDisable() {
        if (stratasExpansion != null && stratasExpansion.isRegistered()) {
            stratasExpansion.unregister();
        }
        if (stratas != null) {
            stratas.flushNow();
        }
        if (database != null) {
            database.close();
        }
    }

    /* ---- helpers ---- */

    /** /strataeconomy saleslog - the same flagged sales the console gets, with a click-to-teleport shortcut. */
    private void sendSalesLog(org.bukkit.command.CommandSender sender) {
        List<SaleLog.Sale> sales = saleLog.history();
        if (sales.isEmpty()) {
            sender.sendMessage("No flagged sales recorded since the last restart.");
            return;
        }
        int count = Math.min(15, sales.size());
        sender.sendMessage(Component.text("Last " + count + " flagged sale(s), newest first (only kept in memory - gone on restart):",
                NamedTextColor.GOLD));
        for (int i = sales.size() - 1; i >= sales.size() - count; i--) {
            SaleLog.Sale sale = sales.get(i);
            long minutesAgo = (System.currentTimeMillis() - sale.whenMillis()) / 60_000L;
            // the folder name isn't always the dimension id (the SMP overworld is folder "world" but
            // dimension "minecraft:overworld") - World.getKey() is what /execute in actually accepts
            org.bukkit.World saleWorld = getServer().getWorld(sale.world());
            String dimension = saleWorld != null ? saleWorld.getKey().asString() : "minecraft:" + sale.world();
            Component teleport = Component.text(" [TP]", NamedTextColor.AQUA, TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand("/execute in " + dimension + " run tp @s "
                            + sale.x() + " " + sale.y() + " " + sale.z()))
                    .hoverEvent(HoverEvent.showText(Component.text("Teleport to " + sale.world() + " "
                            + sale.x() + "," + sale.y() + "," + sale.z())));
            sender.sendMessage(Component.text(minutesAgo + "m ago  ", NamedTextColor.GRAY)
                    .append(Component.text(sale.player() + " ", NamedTextColor.WHITE))
                    .append(Component.text("sold " + sale.items() + " for " + sale.payout() + " Stratas",
                            NamedTextColor.YELLOW))
                    .append(teleport));
        }
    }

    private void bind(String name, org.bukkit.command.CommandExecutor exec) {
        var cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(exec);
        }
    }

    private void saveResourceIfMissing(String path) {
        if (!new File(getDataFolder(), path).exists()) {
            saveResource(path, false);
        }
    }

    public void async(Runnable r) {
        if (isEnabled()) {
            getServer().getScheduler().runTaskAsynchronously(this, r);
        } else {
            r.run();
        }
    }

    public StratasService stratas() {
        return stratas;
    }

    public SpecialOffers specialOffers() {
        return specialOffers;
    }

    public SaleLog saleLog() {
        return saleLog;
    }

    public PurchaseLimits purchaseLimits() {
        return purchaseLimits;
    }

    public Msg msg() {
        return msg;
    }

    public String msgRaw(String key) {
        return messages.getString(key, key);
    }

    public String vaultFormat(long amount) {
        return vault != null ? vault.format(amount)
                : Amounts.format(amount, getConfig().getString("currency.symbol", "♛"),
                getConfig().getString("currency.name-plural", "Stratas"));
    }

    /** Plain "1,234 Stratas" for menu lore - no currency symbol, since the pack font mangles it. */
    public String money(long amount) {
        String plural = amount == 1
                ? getConfig().getString("currency.name-singular", "Strata")
                : getConfig().getString("currency.name-plural", "Stratas");
        return String.format("%,d", amount) + " " + plural;
    }

    /* ---- StrataApi ---- */

    @Override public long getBalance(UUID uuid) { return stratas.getBalance(uuid); }
    @Override public boolean has(UUID uuid, long amount) { return stratas.has(uuid, amount); }
    @Override public void deposit(UUID uuid, long amount) { stratas.deposit(uuid, amount); }
    @Override public boolean withdraw(UUID uuid, long amount) { return stratas.withdraw(uuid, amount); }
    @Override public void set(UUID uuid, long amount) { stratas.set(uuid, amount); }
    @Override public List<Map.Entry<UUID, Long>> top(int limit) { return stratas.top(limit); }
    @Override public String format(long amount) { return vaultFormat(amount); }
    @Override public Prices prices() { return prices; }
    @Override public Auctions auctions() { return auctions; }

    public BuyOrders buyOrders() {
        return buyOrders;
    }

    public int claimAuctionDeliveries(org.bukkit.entity.Player player) {
        return auctions.claimDeliveries(player);
    }

    public void claimBuyOrderDeliveries(org.bukkit.entity.Player player) {
        buyOrders.claimPendingDeliveries(player);
    }
}
