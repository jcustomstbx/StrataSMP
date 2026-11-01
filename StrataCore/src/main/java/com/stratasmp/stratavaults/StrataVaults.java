package com.stratasmp.stratavaults;

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;
import com.stratasmp.strataranks.RankTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Personal vaults: /pv to pick one, /pv &lt;n&gt; to open it, /pvsee for staff to look at someone else's without
 * being able to take anything. How many a player gets comes from their tier (see VaultLimits).
 */
public final class StrataVaults extends StrataModule implements Listener {

    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();

    private VaultStore store;
    private final Set<UUID> saveQueued = new HashSet<>();

    public StrataVaults(StrataCore core) {
        super(core, "StrataVaults");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new VaultStore(this);
        getServer().getPluginManager().registerEvents(this, this);
        for (String name : List.of("pv", "pvsee")) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(this);
                getCommand(name).setTabCompleter(this);
            }
        }
        for (Player online : getServer().getOnlinePlayers()) {
            preload(online);
        }
        getLogger().info("StrataVaults enabled.");
    }

    @Override
    public void onDisable() {
        for (Player online : getServer().getOnlinePlayers()) {
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof VaultHolder holder && !holder.readOnly) {
                store.set(holder.owner, holder.number, holder.inventory.getContents());
                online.closeInventory();
            }
        }
        if (store != null) {
            store.shutdown();
        }
        super.onDisable();
    }

    // ---- state ----

    private void preload(Player player) {
        UUID id = player.getUniqueId();
        store.preload(id, () -> getServer().getPlayer(id) != null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        preload(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        store.evict(event.getPlayer().getUniqueId());
    }

    private int limit(Player player) {
        return VaultLimits.max(getConfig(), RankTier.level(player), player.isOp());
    }

    private void say(CommandSender to, String key, String... replacements) {
        String text = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        to.sendMessage(AMP.deserialize(getConfig().getString("messages.prefix", "") + text));
    }

    private boolean vaultWorld(Player player) {
        String world = player.getWorld().getName();
        return getConfig().getStringList("allowed-worlds").stream().anyMatch(w -> w.equalsIgnoreCase(world));
    }

    // ---- commands ----

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("pvsee")) {
            return see(sender, args);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (!vaultWorld(player)) {
            say(player, "wrong-world");
            return true;
        }
        int max = limit(player);
        if (max < 1) {
            say(player, "none");
            return true;
        }
        if (!store.isLoaded(player.getUniqueId())) {
            say(player, "loading");
            return true;
        }
        if (args.length == 0) {
            openSelector(player, max);
            return true;
        }
        int number = VaultLimits.parseNumber(args[0], max);
        if (number < 0) {
            say(player, "bad-number", "{max}", Integer.toString(max));
            return true;
        }
        open(player, number);
        return true;
    }

    private boolean see(CommandSender sender, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission("stratavaults.see")) {
            sender.sendMessage(Component.text("You can't do that.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /pvsee <player> <number>", NamedTextColor.RED));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("No player found: " + args[0], NamedTextColor.RED));
            return true;
        }
        int number = VaultLimits.parseNumber(args[1], 100);
        if (number < 0) {
            sender.sendMessage(Component.text("Vault number must be a whole number.", NamedTextColor.RED));
            return true;
        }
        UUID id = target.getUniqueId();
        if (store.isLoaded(id)) {
            showReadOnly(viewer, target, number, store.get(id, number));
        } else {
            store.readOffline(id, data -> showReadOnly(viewer, target, number, store.decode(data.get(number), id, number)));
        }
        return true;
    }

    private void showReadOnly(Player viewer, OfflinePlayer target, int number, ItemStack[] contents) {
        if (!viewer.isOnline()) {
            return;
        }
        if (contents == null) {
            say(viewer, "unreadable");
            return;
        }
        VaultHolder holder = new VaultHolder(target.getUniqueId(), number, true);
        holder.inventory = Bukkit.createInventory(holder, VaultStore.SIZE,
                Component.text(String.valueOf(target.getName()) + " - Vault " + number + " (view only)", NamedTextColor.DARK_GRAY));
        holder.inventory.setContents(contents);
        viewer.openInventory(holder.inventory);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("pvsee") && sender.hasPermission("stratavaults.see") && args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player online : getServer().getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(online.getName());
                }
            }
            return names;
        }
        return List.of();
    }

    // ---- menus ----

    private void openSelector(Player player, int max) {
        SelectorHolder holder = new SelectorHolder();
        int rows = Math.max(1, Math.min(6, (max + 8) / 9));
        holder.inventory = Bukkit.createInventory(holder, rows * 9,
                Component.text("Personal Vaults", NamedTextColor.DARK_BLUE, TextDecoration.BOLD));
        for (int n = 1; n <= max && n <= rows * 9; n++) {
            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("Vault " + n, NamedTextColor.AQUA, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Click to open", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
            holder.inventory.setItem(n - 1, item);
        }
        player.openInventory(holder.inventory);
    }

    private void open(Player player, int number) {
        ItemStack[] contents = store.get(player.getUniqueId(), number);
        if (contents == null) {
            say(player, "unreadable");
            return;
        }
        VaultHolder holder = new VaultHolder(player.getUniqueId(), number, false);
        holder.inventory = Bukkit.createInventory(holder, VaultStore.SIZE,
                Component.text("Vault " + number, NamedTextColor.DARK_BLUE, TextDecoration.BOLD));
        holder.inventory.setContents(contents);
        player.openInventory(holder.inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder top = event.getView().getTopInventory().getHolder();
        if (top instanceof SelectorHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()
                    && event.getWhoClicked() instanceof Player player && event.getCurrentItem() != null) {
                int number = event.getRawSlot() + 1;
                if (number >= 1 && number <= limit(player) && vaultWorld(player)) {
                    open(player, number);
                }
            }
        } else if (top instanceof VaultHolder holder) {
            if (holder.readOnly) {
                event.setCancelled(true);
            } else {
                queueSave(holder);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder top = event.getView().getTopInventory().getHolder();
        if (top instanceof SelectorHolder) {
            event.setCancelled(true);
        } else if (top instanceof VaultHolder holder) {
            if (holder.readOnly) {
                event.setCancelled(true);
            } else {
                queueSave(holder);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof VaultHolder holder && !holder.readOnly) {
            store.set(holder.owner, holder.number, holder.inventory.getContents());
        }
    }

    /** Saves a moment after a click so a crash right after moving items can't dupe or lose them. */
    private void queueSave(VaultHolder holder) {
        if (!saveQueued.add(holder.owner)) {
            return;
        }
        getServer().getScheduler().runTaskLater(this, () -> {
            saveQueued.remove(holder.owner);
            if (holder.inventory.getViewers().isEmpty()) {
                return; // already saved by the close handler
            }
            store.set(holder.owner, holder.number, holder.inventory.getContents());
        }, 2L);
    }

    private static final class VaultHolder implements InventoryHolder {
        final UUID owner;
        final int number;
        final boolean readOnly;
        Inventory inventory;

        VaultHolder(UUID owner, int number, boolean readOnly) {
            this.owner = owner;
            this.number = number;
            this.readOnly = readOnly;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class SelectorHolder implements InventoryHolder {
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
