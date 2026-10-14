package com.stratasmp.strataservices;

import com.stratasmp.stratacore.AsyncYamlSaver;
import com.stratasmp.stratacore.AtomicYaml;
import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;
import com.stratasmp.strataeconomy.StrataEconomy;
import com.stratasmp.strataeconomy.gui.Gui;
import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** A menu of things to spend Stratas on (keystones, crate keys, cosmetics...). Every purchase takes Stratas out of the economy. */
public class StrataServices extends StrataModule implements Listener {
    private final PurchaseLedger ledger = new PurchaseLedger();
    private final Map<String, ServiceDef> services = new java.util.LinkedHashMap<>();
    private AsyncYamlSaver saver;
    private ZoneId zone = ZoneId.systemDefault();

    public StrataServices(StrataCore core) {
        super(core, "StrataServices");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        File file = new File(getDataFolder(), "purchases.yml");
        saver = new AsyncYamlSaver(this, file);
        ledger.load(AtomicYaml.load(file, getLogger()));
        load();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("services") != null) getCommand("services").setExecutor(this);
    }

    private void load() {
        reloadConfig();
        try {
            zone = ZoneId.of(getConfig().getString("timezone", ZoneId.systemDefault().getId()));
        } catch (java.time.DateTimeException e) {
            getLogger().warning("Unknown timezone in config; using the server's.");
            zone = ZoneId.systemDefault();
        }
        services.clear();
        for (ServiceDef def : ServiceDef.parseAll(getConfig().getConfigurationSection("services"), getLogger()::warning)) {
            if (services.size() >= Gui.PAGE_SLOTS) {
                getLogger().warning("Only the first " + Gui.PAGE_SLOTS + " services fit in the menu; " + def.id() + " was left out.");
                continue;
            }
            services.put(def.id(), def);
        }
        getLogger().info("Services: " + services.size() + " on offer.");
    }

    private LocalDate today() {
        return LocalDate.now(zone);
    }

    public void open(Player player) {
        ServicesHolder holder = new ServicesHolder();
        Inventory inv = Bukkit.createInventory(holder, Gui.SIZE, Component.text("Stratas Services"));
        holder.inventory = inv;
        StrataEconomy economy = core().module(StrataEconomy.class);
        int slot = 0;
        for (ServiceDef def : services.values()) {
            if (!def.permission().isEmpty() && !player.hasPermission(def.permission())) continue;
            List<String> lore = new ArrayList<>(def.lore());
            lore.add("");
            lore.add("Price: " + (economy == null ? def.price() : economy.money(def.price())));
            if (def.dailyLimit() > 0) {
                lore.add("Today: " + ledger.boughtToday(player.getUniqueId(), def.id(), today()) + "/" + def.dailyLimit());
            }
            lore.add("Click to buy");
            inv.setItem(slot, Gui.button(def.icon(), NamedTextColor.GOLD, def.name(), lore.toArray(String[]::new)));
            holder.slots.put(slot, def.id());
            slot++;
        }
        Gui.frame(inv, 1, 0);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ServicesHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getSlot();
        if (slot == Gui.NAV_CLOSE) {
            player.closeInventory();
            return;
        }
        String id = holder.slots.get(slot);
        if (id == null) return;
        ServiceDef def = services.get(id);
        if (def == null) return;
        String problem = buy(player, def);
        player.sendMessage(problem == null
                ? Component.text("Purchased " + def.name() + ".", NamedTextColor.GREEN)
                : Component.text(problem, NamedTextColor.RED));
        if (player.isOnline()) open(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ServicesHolder) event.setCancelled(true);
    }

    /** @return null on success, otherwise why the purchase didn't happen. */
    String buy(Player player, ServiceDef def) {
        StrataEconomy economy = core().module(StrataEconomy.class);
        if (economy == null || !economy.isEnabled()) return "Stratas aren't available right now.";
        if (!def.permission().isEmpty() && !player.hasPermission(def.permission())) return "You can't buy that.";
        LocalDate today = today();
        if (def.dailyLimit() > 0 && ledger.boughtToday(player.getUniqueId(), def.id(), today) >= def.dailyLimit()) {
            return "You've reached today's limit for " + def.name() + ".";
        }
        if (!economy.stratas().withdraw(player.getUniqueId(), def.price())) {
            return "You need " + economy.money(def.price()) + " for that.";
        }
        ledger.record(player.getUniqueId(), def.id(), today);
        for (String command : def.commands()) {
            String run = command.replace("%player%", player.getName());
            boolean ok;
            try {
                ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), run);
            } catch (RuntimeException e) {
                getLogger().warning("Service command threw for " + player.getName() + ": " + run + " (" + e + ")");
                ok = false;
            }
            if (!ok) {
                getLogger().warning("Service " + def.id() + " command failed for " + player.getName() + ": " + run + " - refunded.");
                economy.stratas().deposit(player.getUniqueId(), def.price());
                ledger.undo(player.getUniqueId(), def.id(), today);
                return "That couldn't be delivered, so you weren't charged.";
            }
        }
        saver.save(ledger.snapshot(today));
        getLogger().info(player.getName() + " bought " + def.id() + " for " + def.price());
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("strataservices.admin")) {
                sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return true;
            }
            load();
            sender.sendMessage(Component.text("Services reloaded.", NamedTextColor.GREEN));
            return true;
        }
        if (sender instanceof Player player) open(player);
        else sender.sendMessage("Only players can open the menu.");
        return true;
    }

    private static final class ServicesHolder implements InventoryHolder {
        final Map<Integer, String> slots = new HashMap<>();
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
