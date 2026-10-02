/*
 * Copyright (c) 2026 JCustoms. All Rights Reserved.
 *
 * This file is proprietary and confidential. No use, copying, modification,
 * or distribution of this file or its compiled output, by any means, is
 * permitted without the prior written permission of JCustoms.
 *
 * See the LICENSE file distributed with this project for the full terms.
 */
package com.stratasmp.stratatrade;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TradeListener implements Listener {

    private final TradeManager manager;

    public TradeListener(TradeManager manager) {
        this.manager = manager;
    }

    /** Every click in a trade window is cancelled first, the manager then decides what it means. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TradeSession.Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (holder.session().sideOf((org.bukkit.entity.Player) event.getWhoClicked()) != holder.side()) {
            return;
        }
        manager.click(holder, event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TradeSession.Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof TradeSession.Holder holder && !holder.session().ended) {
            manager.cancel(holder.session(), holder.side().player().getName() + " closed the trade.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.onQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        manager.onDeath(event);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.restoreOnJoin(event.getPlayer());
    }
}
