package com.stratasmp.strataeconomy.auction;

import com.stratasmp.strataeconomy.StrataEconomy;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/** PDC receipt used to reconcile a saved inventory with a pending delivery after a crash. */
final class DeliveryMarker {
    private DeliveryMarker() { }

    private static NamespacedKey key(StrataEconomy plugin) {
        return new NamespacedKey(plugin, "delivery_id");
    }

    static ItemStack mark(StrataEconomy plugin, ItemStack original, UUID deliveryId) {
        ItemStack marked = original.clone();
        ItemMeta meta = marked.getItemMeta();
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, deliveryId.toString());
        marked.setItemMeta(meta);
        return marked;
    }

    static UUID id(StrataEconomy plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String value = item.getItemMeta().getPersistentDataContainer().get(key(plugin), PersistentDataType.STRING);
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static boolean clear(StrataEconomy plugin, ItemStack item) {
        if (id(plugin, item) == null) return false;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(key(plugin));
        item.setItemMeta(meta);
        return true;
    }

    static void clearFromInventory(StrataEconomy plugin, Player player, UUID deliveryId) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        boolean changed = false;
        for (ItemStack item : contents) {
            if (deliveryId.equals(id(plugin, item))) {
                changed |= clear(plugin, item);
            }
        }
        if (changed) {
            player.getInventory().setStorageContents(contents);
        }
    }

    static boolean present(Player player, StrataEconomy plugin, UUID deliveryId) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (deliveryId.equals(id(plugin, item))) return true;
        }
        return false;
    }
}
