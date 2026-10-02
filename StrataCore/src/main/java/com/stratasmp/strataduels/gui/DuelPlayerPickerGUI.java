package com.stratasmp.strataduels.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public class DuelPlayerPickerGUI implements Listener {
   private final Map<UUID, Inventory> openGuis = new HashMap<>();
   private final Map<UUID, Consumer<Player>> callbacks = new HashMap<>();
   private final Map<UUID, List<UUID>> slotTargets = new HashMap<>();

   public void open(Player viewer, Consumer<Player> onTargetChosen) {
      List<Player> candidates = Bukkit.getOnlinePlayers()
         .stream()
         .filter(p -> !p.getUniqueId().equals(viewer.getUniqueId()))
         .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
         .collect(Collectors.toList());
      int size = Math.max(9, Math.min(54, (candidates.size() + 8) / 9 * 9));
      Inventory inventory = Bukkit.createInventory(null, size, Component.text("Challenge a Player"));

      for (int i = 0; i < size; i++) {
         inventory.setItem(i, this.filler());
      }

      List<UUID> order = new ArrayList<>();
      int slot = 0;

      for (Player candidate : candidates) {
         if (slot >= size) {
            break;
         }

         inventory.setItem(slot, this.buildHead(candidate));
         order.add(candidate.getUniqueId());
         slot++;
      }

      this.openGuis.put(viewer.getUniqueId(), inventory);
      this.callbacks.put(viewer.getUniqueId(), onTargetChosen);
      this.slotTargets.put(viewer.getUniqueId(), order);
      viewer.openInventory(inventory);
   }

   private ItemStack buildHead(Player candidate) {
      ItemStack item = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta meta = (SkullMeta)item.getItemMeta();
      meta.setOwningPlayer(candidate);
      meta.displayName(Component.text(candidate.getName(), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
      meta.lore(List.of(this.line("Click to challenge to a duel.")));
      item.setItemMeta(meta);
      return item;
   }

   private Component line(String text) {
      return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
   }

   private ItemStack filler() {
      ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(Component.text(" "));
      item.setItemMeta(meta);
      return item;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         Inventory inventory = this.openGuis.get(player.getUniqueId());
         if (inventory != null && event.getInventory() == inventory) {
            event.setCancelled(true);
            List<UUID> order = this.slotTargets.get(player.getUniqueId());
            int slot = event.getRawSlot();
            if (order != null && slot >= 0 && slot < order.size()) {
               Player target = Bukkit.getPlayer(order.get(slot));
               if (target != null && target.isOnline()) {
                  Consumer<Player> callback = this.callbacks.remove(player.getUniqueId());
                  this.openGuis.remove(player.getUniqueId());
                  this.slotTargets.remove(player.getUniqueId());
                  player.closeInventory();
                  if (callback != null) {
                     callback.accept(target);
                  }
               }
            }
         }
      }
   }
}
