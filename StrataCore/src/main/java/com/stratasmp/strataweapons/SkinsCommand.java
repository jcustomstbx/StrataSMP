package com.stratasmp.strataweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /skins opens the full skin catalog; /skins owned opens just the skins the player has unlocked. */
public final class SkinsCommand implements CommandExecutor {

    private final SkinsGui ownedGui;
    private final StrataCharmGui catalogGui;

    public SkinsCommand(SkinsGui ownedGui, StrataCharmGui catalogGui) {
        this.ownedGui = ownedGui;
        this.catalogGui = catalogGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        boolean owned = args.length > 0 && (args[0].equalsIgnoreCase("owned") || args[0].equalsIgnoreCase("mine"));
        if (owned) {
            ownedGui.open(player, 0);
        } else {
            catalogGui.openCatalog(player, 0);
        }
        return true;
    }
}
