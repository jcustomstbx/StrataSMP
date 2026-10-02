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

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            plugin.msg().send(sender, "player-not-found", Map.of("player", args[0]));
            return true;
        }

        plugin.strataperks().deposit(target.getUniqueId(), amount);
        plugin.getLogger().info("Granted " + amount + " StrataPerks to " + target.getName() + " (by " + sender.getName() + ").");
        plugin.msg().send(sender, "given", Map.of(
                "amount", String.valueOf(amount),
                "player", target.getName() == null ? args[0] : target.getName(),
                "currency", plugin.currencyName(amount)));

        Player online = target.getPlayer();
        if (online != null) {
            plugin.msg().send(online, "received", Map.of(
                    "amount", String.valueOf(amount),
                    "currency", plugin.currencyName(amount)));
        }
        return true;
    }
}
