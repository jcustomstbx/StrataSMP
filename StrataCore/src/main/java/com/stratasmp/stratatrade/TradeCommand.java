/*
 * Copyright (c) 2026 JCustoms. All Rights Reserved.
 *
 * This file is proprietary and confidential. No use, copying, modification,
 * or distribution of this file or its compiled output, by any means, is
 * permitted without the prior written permission of JCustoms.
 *
 * See the LICENSE file distributed with this project for the full terms.
 */
package com.stratasmp.stratatrade;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TradeCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault());

    private final Plugin plugin;
    private final TradeManager manager;
    private final TradeRepository repository;

    public TradeCommand(Plugin plugin, TradeManager manager, TradeRepository repository) {
        this.plugin = plugin;
        this.manager = manager;
        this.repository = repository;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("stratatrade")) {
            history(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!WorldScope.allows(player.getWorld())) {
            player.sendMessage(Component.text("Player trading is only available in the SMP worlds.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /trade <player>", NamedTextColor.YELLOW));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "accept" -> manager.accept(player, args.length > 1 ? args[1] : null);
            case "deny", "decline" -> manager.deny(player);
            case "cancel" -> manager.cancelFor(player);
            default -> {
                var target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    player.sendMessage(Component.text(args[0] + " isn't online.", NamedTextColor.RED));
                } else {
                    manager.request(player, target);
                }
            }
        }
        return true;
    }

    private void history(CommandSender sender, String[] args) {
        if (!sender.hasPermission("stratatrade.admin")) {
            sender.sendMessage(Component.text("You can't do that.", NamedTextColor.RED));
            return;
        }
        if (args.length != 2 || !args[0].equalsIgnoreCase("history")) {
            sender.sendMessage(Component.text("Usage: /stratatrade history <player>", NamedTextColor.YELLOW));
            return;
        }
        var target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("I don't know a player called " + args[1] + ".", NamedTextColor.RED));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            var entries = repository.recentFor(target.getUniqueId(), 10);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (entries.isEmpty()) {
                    sender.sendMessage(Component.text("No trades found for " + args[1] + ".", NamedTextColor.GRAY));
                    return;
                }
                sender.sendMessage(Component.text("Last " + entries.size() + " trades for " + args[1] + ":", NamedTextColor.GOLD));
                for (var entry : entries) {
                    sender.sendMessage(Component.text(TIME.format(entry.when()) + " ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(entry.firstName() + " gave " + entry.firstGave() + " + "
                            + entry.firstCoins() + " Stratas", NamedTextColor.GRAY))
                        .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(entry.secondName() + " gave " + entry.secondGave() + " + "
                            + entry.secondCoins() + " Stratas", NamedTextColor.GRAY)));
                }
            });
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        var options = new ArrayList<String>();
        if (command.getName().equalsIgnoreCase("stratatrade")) {
            if (args.length == 1) {
                options.add("history");
            } else if (args.length == 2) {
                Bukkit.getOnlinePlayers().forEach(online -> options.add(online.getName()));
            }
        } else if (sender instanceof Player player && !WorldScope.allows(player.getWorld())) {
            return List.of();
        } else if (args.length == 1) {
            options.addAll(List.of("accept", "deny"));
            Bukkit.getOnlinePlayers().forEach(online -> options.add(online.getName()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
            Bukkit.getOnlinePlayers().forEach(online -> options.add(online.getName()));
        }
        var typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }
}
