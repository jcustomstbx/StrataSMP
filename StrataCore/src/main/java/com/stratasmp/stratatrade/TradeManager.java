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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns every open trade and request. All methods run on the main thread. */
public final class TradeManager {

    private record Request(UUID from, long expiresAt) {}

    private static final long REQUEST_COOLDOWN_MILLIS = 3000;

    private final Plugin plugin;
    private final TradeRepository repository;
    private final Economy economy;
    private final TradeItems items;
    private final Map<UUID, TradeSession> sessions = new HashMap<>();
    private final Map<UUID, Request> requests = new HashMap<>();
    private final Map<UUID, Long> lastRequest = new HashMap<>();

    public TradeManager(Plugin plugin, TradeRepository repository, Economy economy, TradeItems items) {
        this.plugin = plugin;
        this.repository = repository;
        this.economy = economy;
        this.items = items;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    TradeSession sessionOf(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /** Lets a player back out of their own trade with /trade cancel. */
    public void cancelFor(Player player) {
        var session = sessions.get(player.getUniqueId());
        if (session == null) {
            tell(player, "You're not in a trade.", NamedTextColor.RED);
            return;
        }
        cancel(session, player.getName() + " cancelled the trade.");
    }

    public boolean isTrading(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    // ---------------------------------------------------------------- requests

    public void request(Player from, Player to) {
        var problem = requestProblem(from, to);
        if (problem != null) {
            tell(from, problem, NamedTextColor.RED);
            return;
        }
        var now = System.currentTimeMillis();
        var existing = requests.get(from.getUniqueId());
        if (existing != null && existing.from().equals(to.getUniqueId()) && existing.expiresAt() > now) {
            requests.remove(from.getUniqueId());
            open(to, from);
            return;
        }
        if (now - lastRequest.getOrDefault(from.getUniqueId(), 0L) < REQUEST_COOLDOWN_MILLIS) {
            tell(from, "Slow down, wait a moment before sending another request.", NamedTextColor.YELLOW);
            return;
        }
        lastRequest.put(from.getUniqueId(), now);
        var seconds = plugin.getConfig().getInt("request-expire-seconds", 30);
        requests.put(to.getUniqueId(), new Request(from.getUniqueId(), now + seconds * 1000L));

        tell(from, "Trade request sent to " + to.getName() + ". It expires in " + seconds + " seconds.", NamedTextColor.GREEN);
        to.sendMessage(Component.text("StrataSMP  •  ", NamedTextColor.GOLD)
            .append(Component.text(from.getName() + " wants to trade with you. ", NamedTextColor.WHITE))
            .append(Component.text("[Accept]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/trade accept " + from.getName())))
            .append(Component.text(" "))
            .append(Component.text("[Deny]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/trade deny"))));
    }

    public void accept(Player target, String fromName) {
        var request = requests.get(target.getUniqueId());
        if (request == null || request.expiresAt() < System.currentTimeMillis()) {
            requests.remove(target.getUniqueId());
            tell(target, "You have no trade request to accept.", NamedTextColor.RED);
            return;
        }
        var from = Bukkit.getPlayer(request.from());
        if (from == null || (fromName != null && !from.getName().equalsIgnoreCase(fromName))) {
            tell(target, "That request is no longer valid.", NamedTextColor.RED);
            return;
        }
        var problem = requestProblem(from, target);
        if (problem != null) {
            tell(target, problem, NamedTextColor.RED);
            return;
        }
        requests.remove(target.getUniqueId());
        open(from, target);
    }

    public void deny(Player target) {
        var request = requests.remove(target.getUniqueId());
        if (request == null) {
            tell(target, "You have no trade request.", NamedTextColor.RED);
            return;
        }
        tell(target, "Trade request declined.", NamedTextColor.GRAY);
        var from = Bukkit.getPlayer(request.from());
        if (from != null) {
            tell(from, target.getName() + " declined your trade request.", NamedTextColor.RED);
        }
    }

    private String requestProblem(Player from, Player to) {
        if (from.getUniqueId().equals(to.getUniqueId())) {
            return "You can't trade with yourself.";
        }
        if (!WorldScope.allows(from.getWorld()) || !WorldScope.allows(to.getWorld())) {
            return "Trading isn't available in this world.";
        }
        if (isTrading(from)) {
            return "You're already in a trade.";
        }
        if (isTrading(to)) {
            return to.getName() + " is already in a trade.";
        }
        var creative = plugin.getConfig().getBoolean("block-creative", true);
        if (creative && (from.getGameMode() == GameMode.CREATIVE || to.getGameMode() == GameMode.CREATIVE)) {
            return "Creative players can't trade.";
        }
        if (!from.getWorld().equals(to.getWorld())) {
            return "You have to be in the same world to trade.";
        }
        var max = plugin.getConfig().getDouble("max-distance", 12);
        if (max > 0 && from.getLocation().distance(to.getLocation()) > max) {
            return "You need to be within " + (int) max + " blocks of " + to.getName() + " to trade.";
        }
        return null;
    }

    // ---------------------------------------------------------------- session

    private void open(Player a, Player b) {
        var session = new TradeSession(a, b);
        var unlock = System.currentTimeMillis() + delayMillis();
        for (var side : List.of(session.first, session.second)) {
            var holder = new TradeSession.Holder(session, side);
            var inventory = Bukkit.createInventory(holder, 54, title(session, side));
            holder.inventory = inventory;
            side.inventory = inventory;
            side.unlockAt = unlock;
            sessions.put(side.player.getUniqueId(), session);
        }
        for (var side : List.of(session.first, session.second)) {
            TradeGui.render(session, side, System.currentTimeMillis(), delayMillis());
            side.player.openInventory(side.inventory);
        }
    }

    public void click(TradeSession.Holder holder, InventoryClickEvent event) {
        var session = holder.session();
        var me = holder.side();
        if (session.ended || !isPlainClick(event)) {
            return;
        }
        var clicked = event.getClickedInventory();
        if (clicked == null) {
            return;
        }
        if (clicked != event.getView().getTopInventory()) {
            if (session.stage == TradeSession.Stage.OFFER) {
                moveToOffer(session, me, event.getSlot(), event.isRightClick() && !event.isShiftClick());
            }
            return;
        }

        var slot = event.getRawSlot();
        switch (slot) {
            case TradeGui.CANCEL -> cancel(session, me.player.getName() + " cancelled the trade.");
            case TradeGui.ACCEPT -> accept(session, me);
            default -> {
                if (session.stage != TradeSession.Stage.OFFER) {
                    return;
                }
                var index = TradeGui.yourIndex(slot);
                if (index >= 0) {
                    takeBack(session, me, index, event.isRightClick() && !event.isShiftClick());
                } else if (slot == TradeGui.COINS_YOURS) {
                    setCoins(session, me, 0);
                } else if (slot == TradeGui.COINS_SMALL || slot == TradeGui.COINS_MEDIUM || slot == TradeGui.COINS_LARGE) {
                    var step = slot == TradeGui.COINS_SMALL ? TradeGui.STEP_SMALL
                        : slot == TradeGui.COINS_MEDIUM ? TradeGui.STEP_MEDIUM : TradeGui.STEP_LARGE;
                    var delta = step * (event.isShiftClick() ? 10 : 1);
                    setCoins(session, me, me.coins + (event.isRightClick() ? -delta : delta));
                }
            }
        }
    }

    private static boolean isPlainClick(InventoryClickEvent event) {
        return switch (event.getClick()) {
            case LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT -> true;
            default -> false;
        };
    }

    void moveToOffer(TradeSession session, TradeSession.Side me, int inventorySlot, boolean single) {
        if (session.ended || session.stage != TradeSession.Stage.OFFER) {
            return;
        }
        if (inventorySlot < 0 || inventorySlot > 35) {
            return;
        }
        var stack = me.player.getInventory().getItem(inventorySlot);
        var problem = items.problemWith(stack);
        if (problem != null) {
            if (stack != null && !stack.getType().isAir()) {
                tell(me.player, problem, NamedTextColor.RED);
            }
            return;
        }
        var amount = single ? 1 : stack.getAmount();
        var moved = stack.clone();
        moved.setAmount(amount);

        var merged = false;
        for (var existing : me.offer) {
            if (existing.isSimilar(moved) && existing.getAmount() + amount <= existing.getMaxStackSize()) {
                existing.setAmount(existing.getAmount() + amount);
                merged = true;
                break;
            }
        }
        if (!merged) {
            if (me.offer.size() >= TradeGui.OFFER_SLOTS) {
                tell(me.player, "Your side of the trade is full.", NamedTextColor.RED);
                return;
            }
            me.offer.add(moved);
        }

        if (amount >= stack.getAmount()) {
            me.player.getInventory().setItem(inventorySlot, null);
        } else {
            stack.setAmount(stack.getAmount() - amount);
        }
        offerChanged(session, me);
    }

    void takeBack(TradeSession session, TradeSession.Side me, int index, boolean single) {
        if (session.ended || session.stage != TradeSession.Stage.OFFER) {
            return;
        }
        if (index >= me.offer.size()) {
            return;
        }
        var stack = me.offer.get(index);
        var amount = single ? 1 : stack.getAmount();
        var portion = stack.clone();
        portion.setAmount(amount);
        var leftover = me.player.getInventory().addItem(portion);
        var notReturned = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
        var returned = amount - notReturned;
        if (returned <= 0) {
            tell(me.player, "You have no room in your inventory.", NamedTextColor.RED);
            return;
        }
        if (returned >= stack.getAmount()) {
            me.offer.remove(index);
        } else {
            stack.setAmount(stack.getAmount() - returned);
        }
        offerChanged(session, me);
    }

    void setCoins(TradeSession session, TradeSession.Side me, int wanted) {
        if (session.ended || session.stage != TradeSession.Stage.OFFER) {
            return;
        }
        var cap = Math.min(plugin.getConfig().getInt("max-stratas", 10_000_000), (int) Math.min(Integer.MAX_VALUE,
            Math.floor(economy.getBalance(me.player))));
        var coins = Math.max(0, Math.min(wanted, cap));
        if (coins == me.coins) {
            if (wanted > cap) {
                tell(me.player, "You can offer at most " + TradeGui.format(cap) + " Stratas.", NamedTextColor.YELLOW);
            }
            return;
        }
        me.coins = coins;
        offerChanged(session, me);
    }

    private void offerChanged(TradeSession session, TradeSession.Side changed) {
        var unlock = System.currentTimeMillis() + delayMillis();
        var other = session.otherOf(changed);
        var hadAccepted = changed.accepted || other.accepted;
        changed.accepted = false;
        other.accepted = false;
        changed.unlockAt = unlock;
        other.unlockAt = unlock;
        persist(changed);
        if (hadAccepted) {
            tell(other.player, changed.player.getName() + " changed their offer, check it again.", NamedTextColor.YELLOW);
        }
        refresh(session);
    }

    void accept(TradeSession session, TradeSession.Side me) {
        var now = System.currentTimeMillis();
        if (now < me.unlockAt) {
            return;
        }
        me.accepted = !me.accepted;
        var other = session.otherOf(me);
        if (!(me.accepted && other.accepted)) {
            TradeGui.renderButtons(me.inventory, session, me, other, now, delayMillis());
            TradeGui.renderButtons(other.inventory, session, other, me, now, delayMillis());
            return;
        }
        if (session.stage == TradeSession.Stage.OFFER) {
            advanceToConfirm(session);
        } else {
            complete(session);
        }
    }

    private void advanceToConfirm(TradeSession session) {
        var nothing = session.first.offer.isEmpty() && session.second.offer.isEmpty()
            && session.first.coins == 0 && session.second.coins == 0;
        if (nothing) {
            resetToOffer(session, "There's nothing in the trade yet.");
            return;
        }
        session.stage = TradeSession.Stage.CONFIRM;
        var unlock = System.currentTimeMillis() + delayMillis();
        for (var side : List.of(session.first, session.second)) {
            side.accepted = false;
            side.unlockAt = unlock;
            side.player.getOpenInventory().setTitle(titleText(session, side));
            tell(side.player, "Final check. Read both sides, then confirm.", NamedTextColor.GOLD);
        }
        refresh(session);
    }

    private void resetToOffer(TradeSession session, String message) {
        session.stage = TradeSession.Stage.OFFER;
        var unlock = System.currentTimeMillis() + delayMillis();
        for (var side : List.of(session.first, session.second)) {
            side.accepted = false;
            side.unlockAt = unlock;
            side.player.getOpenInventory().setTitle(titleText(session, side));
            tell(side.player, message, NamedTextColor.RED);
        }
        refresh(session);
    }

    // ---------------------------------------------------------------- finishing

    private void complete(TradeSession session) {
        var a = session.first;
        var b = session.second;
        var problem = completionProblem(session);
        if (problem != null) {
            resetToOffer(session, problem);
            return;
        }

        var pa = a.player;
        var pb = b.player;
        if (a.coins > 0 && !economy.withdrawPlayer(pa, a.coins).transactionSuccess()) {
            resetToOffer(session, pa.getName() + " no longer has the Stratas.");
            return;
        }
        if (b.coins > 0 && !economy.withdrawPlayer(pb, b.coins).transactionSuccess()) {
            if (a.coins > 0) {
                economy.depositPlayer(pa, a.coins);
            }
            resetToOffer(session, pb.getName() + " no longer has the Stratas.");
            return;
        }
        var firstGave = TradeItems.describe(a.offer);
        var secondGave = TradeItems.describe(b.offer);
        try {
            if (a.coins > 0 && !economy.depositPlayer(pb, a.coins).transactionSuccess()) {
                plugin.getLogger().severe("Deposit of " + a.coins + " Stratas to " + pb.getName() + " failed after a trade, pay it manually.");
            }
            if (b.coins > 0 && !economy.depositPlayer(pa, b.coins).transactionSuccess()) {
                plugin.getLogger().severe("Deposit of " + b.coins + " Stratas to " + pa.getName() + " failed after a trade, pay it manually.");
            }
            TradeItems.give(pb, a.offer);
            TradeItems.give(pa, b.offer);
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "A trade failed part way through. " + pa.getName()
                + " was giving [" + firstGave + ", " + a.coins + " Stratas], " + pb.getName() + " was giving ["
                + secondGave + ", " + b.coins + " Stratas]. Check both inventories.", e);
            finish(session);
            tell(pa, "Something went wrong finishing the trade, ask an admin to check it.", NamedTextColor.RED);
            tell(pb, "Something went wrong finishing the trade, ask an admin to check it.", NamedTextColor.RED);
            return;
        }

        plugin.getLogger().info("TRADE " + pa.getName() + " gave [" + firstGave + ", " + a.coins + " Stratas] and "
            + pb.getName() + " gave [" + secondGave + ", " + b.coins + " Stratas].");
        var firstCoins = a.coins;
        var secondCoins = b.coins;
        repository.submit(() -> repository.logTrade(pa.getUniqueId(), pa.getName(), pb.getUniqueId(), pb.getName(),
            firstGave, secondGave, firstCoins, secondCoins));

        finish(session);
        tell(pa, "Trade complete.", NamedTextColor.GREEN);
        tell(pb, "Trade complete.", NamedTextColor.GREEN);
    }

    private String completionProblem(TradeSession session) {
        var a = session.first;
        var b = session.second;
        if (!a.player.isOnline() || !b.player.isOnline()) {
            return "The other player left.";
        }
        if (!WorldScope.allows(a.player.getWorld()) || !WorldScope.allows(b.player.getWorld())) {
            return "Trading isn't available in this world.";
        }
        if (!a.player.getWorld().equals(b.player.getWorld())) {
            return "You are no longer in the same world.";
        }
        var max = plugin.getConfig().getDouble("max-distance", 12);
        if (max > 0 && a.player.getLocation().distance(b.player.getLocation()) > max) {
            return "You moved too far apart.";
        }
        if (economy.getBalance(a.player) < a.coins) {
            return a.player.getName() + " no longer has the Stratas offered.";
        }
        if (economy.getBalance(b.player) < b.coins) {
            return b.player.getName() + " no longer has the Stratas offered.";
        }
        if (!TradeItems.canHold(b.player, a.offer)) {
            return b.player.getName() + " doesn't have enough inventory space.";
        }
        if (!TradeItems.canHold(a.player, b.offer)) {
            return a.player.getName() + " doesn't have enough inventory space.";
        }
        return null;
    }

    /** Ends the session after a successful swap: nothing is returned, only escrow is cleared. */
    private void finish(TradeSession session) {
        session.ended = true;
        for (var side : List.of(session.first, session.second)) {
            side.offer.clear();
            sessions.remove(side.player.getUniqueId());
            var id = side.player.getUniqueId();
            repository.submit(() -> repository.deleteEscrow(id));
            closeLater(side.player);
        }
    }

    public void cancel(TradeSession session, String reason) {
        if (session.ended) {
            return;
        }
        session.ended = true;
        for (var side : List.of(session.first, session.second)) {
            sessions.remove(side.player.getUniqueId());
            if (!side.offer.isEmpty()) {
                TradeItems.give(side.player, side.offer);
            }
            side.offer.clear();
            var id = side.player.getUniqueId();
            repository.submit(() -> repository.deleteEscrow(id));
            closeLater(side.player);
            tell(side.player, reason + " Your items were returned.", NamedTextColor.RED);
        }
    }

    public void onQuit(Player player) {
        requests.remove(player.getUniqueId());
        var session = sessions.get(player.getUniqueId());
        if (session != null) {
            cancel(session, player.getName() + " left.");
        }
    }

    /** A player who dies mid-trade loses their offered items with the rest of their drops, never into thin air. */
    public void onDeath(PlayerDeathEvent event) {
        var dead = event.getEntity();
        var session = sessions.get(dead.getUniqueId());
        if (session == null) {
            return;
        }
        var side = session.sideOf(dead);
        var offered = new ArrayList<>(side.offer);
        side.offer.clear();
        if (event.getKeepInventory()) {
            TradeItems.give(dead, offered);
        } else {
            offered.forEach(item -> event.getDrops().add(item));
        }
        repository.submit(() -> repository.deleteEscrow(dead.getUniqueId()));
        cancel(session, dead.getName() + " died.");
    }

    public void cancelAll(String reason) {
        for (var session : new HashSet<>(sessions.values())) {
            cancel(session, reason);
        }
    }

    /** Returns anything a crash left in escrow the next time the owner joins. */
    public void restoreOnJoin(Player player) {
        var id = player.getUniqueId();
        repository.submit(() -> {
            var data = repository.loadEscrow(id);
            if (data.isEmpty()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || sessions.containsKey(id)) {
                    return;
                }
                var returned = List.of(ItemStack.deserializeItemsFromBytes(data.get()));
                TradeItems.give(player, returned);
                plugin.getLogger().warning("Returned " + returned.size() + " escrowed stack(s) to " + player.getName()
                    + " after an interrupted trade.");
                tell(player, "Items from an interrupted trade were returned to you.", NamedTextColor.YELLOW);
                repository.submit(() -> repository.deleteEscrow(id));
            });
        });
    }

    // ---------------------------------------------------------------- upkeep

    private void tick() {
        var now = System.currentTimeMillis();
        for (var session : new HashSet<>(sessions.values())) {
            if (session.ended) {
                continue;
            }
            var problem = upkeepProblem(session);
            if (problem != null) {
                cancel(session, problem);
                continue;
            }
            if (session.first.unlockAt > now - 1000 || session.second.unlockAt > now - 1000) {
                TradeGui.renderButtons(session.first.inventory, session, session.first, session.second, now, delayMillis());
                TradeGui.renderButtons(session.second.inventory, session, session.second, session.first, now, delayMillis());
            }
        }
        requests.values().removeIf(request -> request.expiresAt() < now);
    }

    private String upkeepProblem(TradeSession session) {
        var a = session.first.player;
        var b = session.second.player;
        if (!a.isOnline() || !b.isOnline()) {
            return "The other player left.";
        }
        if (!WorldScope.allows(a.getWorld()) || !WorldScope.allows(b.getWorld())) {
            return "Trading isn't available in this world.";
        }
        if (!a.getWorld().equals(b.getWorld())) {
            return "You are no longer in the same world.";
        }
        var max = plugin.getConfig().getDouble("max-distance", 12);
        if (max > 0 && a.getLocation().distance(b.getLocation()) > max + 3) {
            return "You moved too far apart.";
        }
        if (plugin.getConfig().getBoolean("block-creative", true)
            && (a.getGameMode() == GameMode.CREATIVE || b.getGameMode() == GameMode.CREATIVE)) {
            return "Creative players can't trade.";
        }
        return null;
    }

    private void persist(TradeSession.Side side) {
        var id = side.player.getUniqueId();
        if (side.offer.isEmpty()) {
            repository.submit(() -> repository.deleteEscrow(id));
            return;
        }
        var data = ItemStack.serializeItemsAsBytes(new ArrayList<>(side.offer));
        repository.submit(() -> repository.saveEscrow(id, data));
    }

    private void refresh(TradeSession session) {
        var now = System.currentTimeMillis();
        TradeGui.render(session, session.first, now, delayMillis());
        TradeGui.render(session, session.second, now, delayMillis());
    }

    private String titleText(TradeSession session, TradeSession.Side side) {
        var partner = session.otherOf(side).player.getName();
        return session.stage == TradeSession.Stage.CONFIRM
            ? "StrataSMP • Confirm with " + partner : "StrataSMP • Trade with " + partner;
    }

    private Component title(TradeSession session, TradeSession.Side side) {
        return Component.text("StrataSMP", NamedTextColor.GOLD).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
            .append(Component.text("  •  ", NamedTextColor.DARK_GRAY))
            .append(Component.text(session.stage == TradeSession.Stage.CONFIRM ? "Confirm trade" : "Player trade",
                NamedTextColor.AQUA))
            .append(Component.text(" with ", NamedTextColor.GRAY))
            .append(Component.text(session.otherOf(side).player.getName(), NamedTextColor.WHITE));
    }

    /** Closing a window from inside its own click event misbehaves, so it waits a tick unless the plugin is going away. */
    private void closeLater(Player player) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
        } else {
            player.closeInventory();
        }
    }

    private long delayMillis() {
        return Math.max(0, plugin.getConfig().getInt("accept-delay-seconds", 5)) * 1000L;
    }

    private static void tell(Player player, String message, NamedTextColor color) {
        player.sendMessage(Component.text(message, color));
    }
}
