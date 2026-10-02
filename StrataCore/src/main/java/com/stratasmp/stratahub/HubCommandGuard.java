package com.stratasmp.stratahub;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Blocks SMP-economy commands (shop, auction, vaults, homes, etc.) while a player is in the hub world - duels, chat and messaging still work. */
public final class HubCommandGuard implements Listener {

    // Market and player-trade commands are never available in the hub, including for ops.
    private static final Set<String> ALWAYS_BLOCKED = Set.of(
            "shop", "sell", "sellall", "sellgui", "ah", "auctionhouse", "auction", "trade", "trading"
    );

    private final StrataHub plugin;

    public HubCommandGuard(StrataHub plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String hubWorld = plugin.getConfig().getString("hub-world", "hub");
        if (!player.getWorld().getName().equals(hubWorld)) {
            return;
        }

        String message = event.getMessage().substring(1);
        String label = message.split("\\s+", 2)[0].toLowerCase(Locale.ENGLISH);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }

        List<String> blocked = plugin.getConfig().getStringList("hub-command-block");
        boolean isAlwaysBlocked = ALWAYS_BLOCKED.contains(label);
        if (isAlwaysBlocked || (blocked.contains(label) && !player.hasPermission("stratahub.hubcommands.bypass"))) {
            event.setCancelled(true);
            plugin.msg().send(player, "hub-command-blocked");
        }
    }
}
