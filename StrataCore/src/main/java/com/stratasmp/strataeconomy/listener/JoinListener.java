package com.stratasmp.strataeconomy.listener;

import com.stratasmp.strataeconomy.StrataEconomy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class JoinListener implements Listener {

    private final StrataEconomy plugin;

    public JoinListener(StrataEconomy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        // already off the main thread - safe to hit the DB
        plugin.stratas().preload(event.getUniqueId(), event.getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.stratas().flushAsync();
        // keep the balance cached briefly for reconnects; a periodic sweep could
        // evict, but the map is tiny and preload refreshes on next join anyway.
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> {
                    plugin.claimAuctionDeliveries(event.getPlayer());
                    plugin.claimBuyOrderDeliveries(event.getPlayer());
                });
    }
}
