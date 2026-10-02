package com.stratasmp.stratahub;

import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * AntiFloodGuard (Zurned's, not ours) already broadcasts a plain-text location whenever it drains
 * flood/dupe activity, to anyone with antifloodguard.alerts (default op). Rather than patch that jar,
 * this attaches a log handler directly to it and follows up with a clickable teleport - no access to
 * its source needed, just its own logger, which every plugin exposes through the public JUL API.
 */
final class AntiFloodClickable {

    private static final Pattern LOCATION = Pattern.compile(
            "Flood/dupe activity blocked and drained near (\\S+) (-?\\d+),(-?\\d+),(-?\\d+)");

    private final StrataHub plugin;

    AntiFloodClickable(StrataHub plugin) {
        this.plugin = plugin;
    }

    void hook() {
        if (!plugin.getConfig().getBoolean("antiflood-clickable", true)) {
            return;
        }
        Plugin antiFloodGuard = Bukkit.getPluginManager().getPlugin("AntiFloodGuard");
        if (antiFloodGuard == null) {
            return;
        }
        antiFloodGuard.getLogger().addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                String message = record.getMessage();
                if (message == null) {
                    return;
                }
                Matcher matcher = LOCATION.matcher(message);
                if (!matcher.find()) {
                    return;
                }
                String world = matcher.group(1);
                int x = Integer.parseInt(matcher.group(2));
                int y = Integer.parseInt(matcher.group(3));
                int z = Integer.parseInt(matcher.group(4));
                // publish() can run on whatever thread triggered the flood check, not necessarily
                // the main thread - Bukkit.getOnlinePlayers()/sendMessage need to be on it
                Bukkit.getScheduler().runTask(plugin, () -> notifyAlertHolders(world, x, y, z));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        plugin.getLogger().info("Hooked AntiFloodGuard's alerts for click-to-teleport.");
    }

    private void notifyAlertHolders(String world, int x, int y, int z) {
        // the world's folder name isn't always its dimension id - the SMP overworld is folder "world" but
        // dimension "minecraft:overworld" - World.getKey() is the id /execute in actually accepts
        org.bukkit.World bukkitWorld = Bukkit.getWorld(world);
        String dimension = bukkitWorld != null ? bukkitWorld.getKey().asString() : "minecraft:" + world;
        Component teleport = Component.text("[TP]", NamedTextColor.AQUA, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/execute in " + dimension + " run tp @s " + x + " " + y + " " + z))
                .hoverEvent(HoverEvent.showText(Component.text("Teleport to " + world + " " + x + "," + y + "," + z)));
        Component message = Component.text("[AntiFloodGuard] ", NamedTextColor.RED)
                .append(Component.text("Click to check that location  ", NamedTextColor.GRAY))
                .append(teleport);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("antifloodguard.alerts")) {
                player.sendMessage(message);
            }
        }
    }
}
