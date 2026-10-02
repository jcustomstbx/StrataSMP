package com.stratasmp.strataeconomy.command;

import com.stratasmp.strataeconomy.StrataEconomy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class PayCommand implements CommandExecutor {

    private final StrataEconomy plugin;

    public PayCommand(StrataEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player payer)) {
            plugin.msg().send(sender, "players-only");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("/pay <player> <amount>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.msg().send(sender, "player-not-found", Map.of("player", args[0]));
            return true;
        }
        if (target.getUniqueId().equals(payer.getUniqueId())) {
            plugin.msg().send(sender, "pay-self");
            return true;
        }
        long amount;
        try {
            amount = com.stratasmp.strataeconomy.currency.Amounts.parse(args[1]);
        } catch (NumberFormatException e) {
            plugin.msg().send(sender, "invalid-amount", Map.of("amount", args[1]));
            return true;
        }
        if (amount <= 0) {
            plugin.msg().send(sender, "invalid-amount", Map.of("amount", args[1]));
            return true;
        }
        if (!plugin.stratas().withdraw(payer.getUniqueId(), amount)) {
            plugin.msg().send(sender, "pay-insufficient", Map.of("amount", plugin.money(amount)));
            return true;
        }
        plugin.stratas().deposit(target.getUniqueId(), amount);
        String formatted = plugin.money(amount);
        plugin.msg().send(payer, "pay-sent", Map.of("amount", formatted, "player", target.getName()));
        plugin.msg().send(target, "pay-received", Map.of("amount", formatted, "player", payer.getName()));
        return true;
    }
}
