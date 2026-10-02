package com.stratasmp.strataeconomy.command;

import com.stratasmp.strataeconomy.StrataEconomy;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class StratasCommand implements CommandExecutor {

    private final StrataEconomy plugin;
    private final EcoCommand eco;

    public StratasCommand(StrataEconomy plugin) {
        this.plugin = plugin;
        this.eco = new EcoCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // "/stratas give <player> <amount>" is how admins used to hand out Stratas; it now forwards to /eco
        if (args.length >= 1 && isAdminAction(args[0]) && sender.hasPermission("strataeconomy.admin")) {
            return eco.onCommand(sender, command, label, args);
        }
        if (args.length >= 1) {
            OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[0]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                plugin.msg().send(sender, "player-not-found", Map.of("player", args[0]));
                return true;
            }
            long bal = plugin.stratas().getBalance(target.getUniqueId());
            plugin.msg().send(sender, "balance-other", Map.of(
                    "player", target.getName() == null ? args[0] : target.getName(),
                    "balance", plugin.money(bal)));
            return true;
        }
        if (!(sender instanceof Player p)) {
            plugin.msg().send(sender, "players-only");
            return true;
        }
        plugin.msg().send(p, "balance-self", Map.of("balance", plugin.money(plugin.stratas().getBalance(p.getUniqueId()))));
        return true;
    }

    private static boolean isAdminAction(String arg) {
        return switch (arg.toLowerCase()) {
            case "give", "take", "set", "reset" -> true;
            default -> false;
        };
    }
}
