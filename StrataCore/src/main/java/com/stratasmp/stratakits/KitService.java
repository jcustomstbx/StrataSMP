package com.stratasmp.stratakits;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import com.stratasmp.stratacore.StrataModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class KitService {

    private final StrataModule plugin;
    private final KitCatalog catalog;
    private final ClaimStore claims;

    KitService(StrataModule plugin, KitCatalog catalog, ClaimStore claims) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.claims = claims;
    }

    KitCatalog catalog() {
        return catalog;
    }

    ClaimStore claims() {
        return claims;
    }

    java.util.logging.Logger logger() {
        return plugin.getLogger();
    }

    boolean canClaimHere(Player player) {
        String world = player.getWorld().getName();
        return plugin.getConfig().getStringList("allowed-worlds").stream().anyMatch(w -> w.equalsIgnoreCase(world));
    }

    long cooldownMillis() {
        return plugin.getConfig().getLong("cooldown-seconds", 259200L) * 1000L;
    }

    /** Milliseconds until this player can claim the kit again; 0 when it's ready. */
    long remaining(Player player, Kit kit) {
        long last = claims.lastClaim(player.getUniqueId(), kit.id());
        return Math.max(0L, last + cooldownMillis() - System.currentTimeMillis());
    }

    private int freeSlots(Player player) {
        int free = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                free++;
            }
        }
        return free;
    }

    /** Hands over the player's own kit if the world, cooldown and inventory space all allow it. */
    void claim(Player player) {
        Kit kit = catalog.kitFor(player);
        if (kit == null) {
            player.sendMessage(Component.text("Kits come with a rank - see /store.", NamedTextColor.RED));
            return;
        }
        if (!canClaimHere(player)) {
            player.sendMessage(Component.text("Kits can only be claimed on the SMP.", NamedTextColor.RED));
            return;
        }
        long wait = remaining(player, kit);
        if (wait > 0) {
            player.sendMessage(Component.text("Your kit is ready again in " + Text.duration(wait) + ".", NamedTextColor.RED));
            return;
        }
        int needed = kit.stacksNeeded();
        if (freeSlots(player) < needed) {
            player.sendMessage(Component.text("You need " + needed + " free inventory slots to claim this kit.", NamedTextColor.RED));
            return;
        }

        claims.record(player.getUniqueId(), kit.id(), System.currentTimeMillis());

        List<ItemStack> stacks = new ArrayList<>();
        for (KitItem item : kit.items()) {
            stacks.addAll(item.build(plugin.getLogger()));
        }
        for (ItemStack stack : stacks) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            leftover.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
        for (Reward reward : kit.rewards()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), reward.command().replace("{player}", player.getName()));
        }
        plugin.getLogger().info(player.getName() + " claimed the " + kit.id() + " kit.");
        player.sendMessage(Text.color("&aYou claimed the " + kit.displayName() + " &akit! Back again in " + Text.duration(cooldownMillis()) + "."));
    }
}
