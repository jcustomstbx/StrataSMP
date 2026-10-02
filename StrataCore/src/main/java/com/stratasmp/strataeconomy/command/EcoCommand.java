package com.stratasmp.strataeconomy.command;

import com.stratasmp.strataeconomy.StrataEconomy;
import com.stratasmp.strataeconomy.currency.Amounts;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Map;

public final class EcoCommand implements CommandExecutor {

    private final StrataEconomy plugin;

    public EcoCommand(StrataEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("strataeconomy.admin")) {
            plugin.msg().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("/eco <give|take|set|reset> <player> [amount]");
            return true;
        }
        String action = args[0].toLowerCase();
        OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            plugin.msg().send(sender, "player-not-found", Map.of("player", args[1]));
            return true;
        }

        if (action.equals("reset")) {
            long start = plugin.getConfig().getLong("currency.starting-balance", 0);
            plugin.stratas().set(target.getUniqueId(), start);
            plugin.msg().send(sender, "eco-reset", Map.of("player", args[1]));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("/eco " + action + " <player> <amount>");
            return true;
        }
        long amount;
        try {
            amount = Amounts.parse(args[2]);
        } catch (NumberFormatException e) {
            plugin.msg().send(sender, "invalid-amount", Map.of("amount", args[2]));
            return true;
        }

        switch (action) {
            case "give" -> {
                plugin.stratas().deposit(target.getUniqueId(), amount);
                plugin.msg().send(sender, "eco-given", Map.of("player", args[1], "amount", plugin.money(amount)));
            }
            case "take" -> {
                plugin.stratas().withdraw(target.getUniqueId(), amount);
                plugin.msg().send(sender, "eco-taken", Map.of("player", args[1], "amount", plugin.money(amount)));
            }
            case "set" -> {
                plugin.stratas().set(target.getUniqueId(), amount);
                plugin.msg().send(sender, "eco-set", Map.of("player", args[1], "amount", plugin.money(amount)));
            }
            default -> sender.sendMessage("/eco <give|take|set|reset> <player> [amount]");
        }
        return true;
    }
}
