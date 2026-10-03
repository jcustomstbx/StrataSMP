package com.stratasmp.strataperks;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/** {@code /givestrataperks <player> <amount>} - the command the store (Tebex) runs on a purchase. */
public final class GiveStrataPerksCommand implements CommandExecutor {

    private final StrataPerks plugin;

    public GiveStrataPerksCommand(StrataPerks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("strataperks.admin")) {
            plugin.msg().send(sender, "no-permission");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("Usage: /givestrataperks <player> <amount>");
            return true;
        }
        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("Amount must be a whole number.");
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage("Amount must be positive.");
            return true;
        }

        final long grant = amount;
        com.stratasmp.stratacore.NameLookup.resolve(plugin, args[0], (uuid, name) -> {
            plugin.strataperks().deposit(uuid, grant);
            plugin.getLogger().info("Granted " + grant + " StrataPerks to " + name + " (by " + sender.getName() + ").");
            plugin.msg().send(sender, "given", Map.of(
                    "amount", String.valueOf(grant),
                    "player", name,
                    "currency", plugin.currencyName(grant)));
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                plugin.msg().send(online, "received", Map.of(
                        "amount", String.valueOf(grant),
                        "currency", plugin.currencyName(grant)));
            }
        }, () -> plugin.msg().send(sender, "player-not-found", Map.of("player", args[0])));
        return true;
    }
}
