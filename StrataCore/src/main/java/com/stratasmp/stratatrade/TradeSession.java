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

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** One live trade between two players. Only ever touched on the main thread. */
public final class TradeSession {

    public enum Stage { OFFER, CONFIRM }

    /** What one player has put in, plus their own view of the window. */
    public static final class Side {
        final Player player;
        final List<ItemStack> offer = new ArrayList<>();
        int coins;
        boolean accepted;
        long unlockAt;
        Inventory inventory;

        Side(Player player) {
            this.player = player;
        }

        public Player player() {
            return player;
        }
    }

    /** Ties an open window back to its session and to the player looking at it. */
    public static final class Holder implements InventoryHolder {
        final TradeSession session;
        final Side side;
        Inventory inventory;

        Holder(TradeSession session, Side side) {
            this.session = session;
            this.side = side;
        }

        public TradeSession session() {
            return session;
        }

        public Side side() {
            return side;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    final Side first;
    final Side second;
    Stage stage = Stage.OFFER;
    boolean ended;

    TradeSession(Player first, Player second) {
        this.first = new Side(first);
        this.second = new Side(second);
    }

    Side sideOf(Player player) {
        if (first.player.getUniqueId().equals(player.getUniqueId())) {
            return first;
        }
        return second.player.getUniqueId().equals(player.getUniqueId()) ? second : null;
    }

    Side otherOf(Side side) {
        return side == first ? second : first;
    }
}
