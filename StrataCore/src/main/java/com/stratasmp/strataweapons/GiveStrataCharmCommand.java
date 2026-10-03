package com.stratasmp.strataweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /givestratacharm <player> <amount>} - the command the store (Tebex) runs on a purchase. */
public final class GiveStrataCharmCommand implements CommandExecutor {

    private final StrataWeapons plugin;
    private final StrataCharmService charms;

    public GiveStrataCharmCommand(StrataWeapons plugin, StrataCharmService charms) {
        this.plugin = plugin;
        this.charms = charms;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /givestratacharm <player> <amount>", NamedTextColor.RED));
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Amount must be a whole number.", NamedTextColor.RED));
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(Component.text("Amount must be positive.", NamedTextColor.RED));
            return true;
        }

        final int grant = amount;
        com.stratasmp.stratacore.NameLookup.resolve(plugin, args[0], (uuid, name) -> {
            charms.deposit(uuid, grant);
            plugin.getLogger().info("Granted " + grant + " StrataCharm(s) to " + name + " (by " + sender.getName() + ").");
            sender.sendMessage(Component.text("Gave " + grant + " StrataCharm" + (grant == 1 ? "" : "s") + " to " + name + ".",
                    NamedTextColor.GREEN));
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                online.sendMessage(Component.text("You received " + grant + " StrataCharm" + (grant == 1 ? "" : "s")
                        + "! Use /stratacharm to unlock a skin.", NamedTextColor.LIGHT_PURPLE));
            }
        }, () -> sender.sendMessage(Component.text("No player found: " + args[0], NamedTextColor.RED)));
        return true;
    }
}
