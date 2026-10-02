package com.stratasmp.stratacratevault;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** Prizes are won in the Hub but only claimable on the SMP, so nudge players when they arrive there. */
final class ReminderListener implements Listener {

    private final StrataCrateVault plugin;
    private final VaultStore store;

    ReminderListener(StrataCrateVault plugin, VaultStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        remind(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        remind(event.getPlayer());
    }

    private void remind(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !plugin.isVaultWorld(player)) {
                return;
            }
            int waiting = store.totalItems(player.getUniqueId());
            if (waiting > 0) {
                player.sendMessage(Component.text("You have " + waiting + " item(s) waiting in your Crate Vault - use /cratevault to claim them.",
                        NamedTextColor.LIGHT_PURPLE));
            }
        }, 40L);
    }
}
