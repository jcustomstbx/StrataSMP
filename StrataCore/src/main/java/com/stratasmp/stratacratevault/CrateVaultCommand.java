package com.stratasmp.stratacratevault;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * {@code /cratevault} opens the vault. {@code /cratevault give <player> <MATERIAL> <amount> [enchant:level,...]}
 * is what crate prizes run (as console) to deposit an item.
 */
final class CrateVaultCommand implements CommandExecutor {

    private final StrataCrateVault plugin;
    private final VaultStore store;
    private final VaultGui gui;

    CrateVaultCommand(StrataCrateVault plugin, VaultStore store, VaultGui gui) {
        this.plugin = plugin;
        this.store = store;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            return give(sender, args);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        gui.open(player, 0);
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("stratacratevault.admin")) {
            sender.sendMessage(Component.text("You can't do that.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 4 || args.length > 5) {
            sender.sendMessage(Component.text("Usage: /cratevault give <player> <MATERIAL> <amount> [enchant:level,...]", NamedTextColor.RED));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(Component.text("No player found: " + args[1], NamedTextColor.RED));
            return true;
        }
        Material material = Material.matchMaterial(args[2]);
        if (material == null || !material.isItem() || material.isAir()) {
            sender.sendMessage(Component.text("Unknown item: " + args[2], NamedTextColor.RED));
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Amount must be a whole number.", NamedTextColor.RED));
            return true;
        }
        if (amount < 1 || amount > 100000) {
            sender.sendMessage(Component.text("Amount must be between 1 and 100000.", NamedTextColor.RED));
            return true;
        }
        Map<String, Integer> enchants = new HashMap<>();
        if (args.length == 5) {
            for (String part : args[4].split(",")) {
                String[] pair = part.split(":");
                int level;
                try {
                    level = Integer.parseInt(pair[1]);
                } catch (RuntimeException e) {
                    sender.sendMessage(Component.text("Bad enchant '" + part + "' - use name:level.", NamedTextColor.RED));
                    return true;
                }
                String name = pair[0].toLowerCase();
                if (Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name)) == null || level < 1 || level > 255) {
                    sender.sendMessage(Component.text("Unknown enchant or level: " + part, NamedTextColor.RED));
                    return true;
                }
                enchants.put(name, level);
            }
        }

        store.deposit(target.getUniqueId(), material, amount, enchants);
        plugin.getLogger().info("Deposited " + amount + "x " + material.name() + " " + enchants + " into "
                + target.getName() + "'s Crate Vault.");
        return true;
    }
}
