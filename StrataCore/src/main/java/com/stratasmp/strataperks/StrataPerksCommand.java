package com.stratasmp.strataperks;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class StrataPerksCommand implements CommandExecutor {

    private final StrataPerks plugin;

    public StrataPerksCommand(StrataPerks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length >= 1) {
            OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[0]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                plugin.msg().send(sender, "player-not-found", Map.of("player", args[0]));
                return true;
            }
            long balance = plugin.strataperks().getBalance(target.getUniqueId());
            plugin.msg().send(sender, "balance-other", Map.of(
                    "player", target.getName() == null ? args[0] : target.getName(),
                    "balance", String.valueOf(balance),
                    "currency", plugin.currencyName(balance)));
            return true;
        }
        if (!(sender instanceof Player p)) {
            plugin.msg().send(sender, "players-only");
            return true;
        }
        long balance = plugin.strataperks().getBalance(p.getUniqueId());
        plugin.msg().send(p, "balance-self", Map.of(
                "balance", String.valueOf(balance),
                "currency", plugin.currencyName(balance)));
        return true;
    }
}
