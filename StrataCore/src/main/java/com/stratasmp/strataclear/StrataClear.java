package com.stratasmp.strataclear;

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Clears dropped items on a timer, with a countdown warning. /clearitems [now|status]. */
public final class StrataClear extends StrataModule {

    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();

    private ClearCountdown countdown;
    private BukkitTask task;

    public StrataClear(StrataCore core) {
        super(core, "StrataClear");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("clearitems").setExecutor(this);
        getCommand("clearitems").setTabCompleter(this);
        if (!getConfig().getBoolean("enabled", true)) {
            getLogger().info("StrataClear is disabled in its config.");
            return;
        }
        int minutes = Math.max(1, getConfig().getInt("interval-minutes", 10));
        countdown = new ClearCountdown(minutes * 60, getConfig().getIntegerList("warnings"));
        task = getServer().getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        getLogger().info("Clearing dropped items every " + minutes + " minute(s).");
    }

    @Override
    public void onDisable() {
        if (task != null) {
            task.cancel();
        }
        super.onDisable();
    }

    private void tick() {
        ClearCountdown.Event event = countdown.tick();
        switch (event.kind()) {
            case WARN -> broadcast(getConfig().getString("messages.warning", "Dropped items will be cleared in {seconds} second(s)!")
                    .replace("{seconds}", Integer.toString(event.secondsLeft())));
            case CLEAR -> clearNow();
            default -> {
            }
        }
    }

    /** Removes dropped items from the configured worlds and tells everyone how many went. */
    private void clearNow() {
        int count = clear();
        String key = count > 0 ? "messages.cleared" : "messages.nothing";
        broadcast(getConfig().getString(key, "Cleared {count} dropped item(s).").replace("{count}", Integer.toString(count)));
    }

    int clear() {
        List<String> only = getConfig().getStringList("worlds");
        Set<String> allowed = new HashSet<>();
        for (String name : only) {
            allowed.add(name.toLowerCase(Locale.ROOT));
        }
        boolean keepNamed = getConfig().getBoolean("keep.named-items", true);
        int youngTicks = Math.max(0, getConfig().getInt("keep.newer-than-seconds", 5)) * 20;
        int removed = 0;
        for (World world : getServer().getWorlds()) {
            if (!allowed.isEmpty() && !allowed.contains(world.getName().toLowerCase(Locale.ROOT))) {
                continue;
            }
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (item.getTicksLived() < youngTicks) {
                    continue;
                }
                ItemStack stack = item.getItemStack();
                if (keepNamed && stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
                    continue;
                }
                removed += Math.max(1, stack.getAmount());
                item.remove();
            }
        }
        return removed;
    }

    private void broadcast(String text) {
        String prefix = getConfig().getString("messages.prefix", "&9&lClear &8» &7");
        Bukkit.broadcast(AMP.deserialize(prefix + text));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("strata.clear")) {
            sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return true;
        }
        String arg = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (arg) {
            case "now" -> {
                if (countdown != null) {
                    countdown.reset();
                }
                clearNow();
            }
            case "status" -> sender.sendMessage(AMP.deserialize(getConfig().getString("messages.prefix", "&9&lClear &8» &7")
                    + (countdown == null ? "Item clearing is disabled."
                    : "Next clear in &b" + countdown.secondsLeft() + " &7second(s).")));
            default -> sender.sendMessage(Component.text("Usage: /clearitems [now|status]", NamedTextColor.YELLOW));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 && sender.hasPermission("strata.clear") ? List.of("now", "status") : List.of();
    }
}
