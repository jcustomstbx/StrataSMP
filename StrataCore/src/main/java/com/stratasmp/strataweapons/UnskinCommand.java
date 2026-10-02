package com.stratasmp.strataweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class UnskinCommand implements CommandExecutor {

    private final WeaponCatalog catalog;

    public UnskinCommand(WeaponCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            player.sendMessage(Component.text("Hold the skinned weapon or armour piece you want to reset.", NamedTextColor.RED));
            return true;
        }
        String key = catalog.removeSkinFrom(held);
        if (key == null) {
            player.sendMessage(Component.text("That item doesn't have a skin on it.", NamedTextColor.RED));
            return true;
        }
        player.getInventory().setItemInMainHand(held);
        player.sendMessage(Component.text("Removed ", NamedTextColor.GREEN)
                .append(Component.text(catalog.displayNameOf(key), NamedTextColor.GOLD))
                .append(Component.text(" from your " + held.getType().name().toLowerCase().replace('_', ' ')
                        + ". You still own the skin - apply it again from /skins.", NamedTextColor.GREEN)));
        return true;
    }
}
