package com.stratasmp.strataduels.gui;

import com.stratasmp.strataduels.kit.KitManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;
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

public class KitSelectGUI implements Listener {
   // where the buttons sit for 0..6 kits, so two FFA kits are centred instead of squeezed to one side
   private static final int[][] LAYOUTS = new int[][]{{}, {13}, {12, 14}, {11, 13, 15}, {10, 12, 14, 16}, {11, 12, 13, 14, 15}, {10, 11, 12, 14, 15, 16}};
   private final KitManager kitManager;
   private final Map<UUID, Inventory> openGuis = new HashMap<>();
   private final Map<UUID, IntConsumer> callbacks = new HashMap<>();
   private final Map<UUID, Map<Integer, Integer>> slotToKit = new HashMap<>();

   public KitSelectGUI(KitManager kitManager) {
      this.kitManager = kitManager;
   }

   /** Every kit slot, set up or not - the picker the ranked queue and challenges use. */
   public void open(Player viewer, IntConsumer onKitChosen) {
      List<Integer> all = new ArrayList<>();
      for (int kit = 1; kit <= KitManager.MAX_KITS; kit++) {
         all.add(kit);
      }
      this.open(viewer, all, Map.of(), onKitChosen);
   }

   /** Only the listed kits, optionally with a short label each (the FFA offers just its own kits). */
   public void open(Player viewer, List<Integer> kits, Map<Integer, String> labels, IntConsumer onKitChosen) {
      Inventory inventory = Bukkit.createInventory(null, 27, Component.text("Choose a Kit"));

      for (int i = 0; i < 27; i++) {
         inventory.setItem(i, this.filler());
      }

      int count = Math.min(kits.size(), KitManager.MAX_KITS);
      int[] slots = LAYOUTS[count];
      Map<Integer, Integer> mapping = new HashMap<>();
      for (int i = 0; i < count; i++) {
         int kit = kits.get(i);
         inventory.setItem(slots[i], this.buildIcon(kit, labels.get(kit)));
         mapping.put(slots[i], kit);
      }

      this.openGuis.put(viewer.getUniqueId(), inventory);
      this.callbacks.put(viewer.getUniqueId(), onKitChosen);
      this.slotToKit.put(viewer.getUniqueId(), mapping);
      viewer.openInventory(inventory);
   }

   private ItemStack buildIcon(int kit, String label) {
      boolean configured = this.kitManager.hasKit(kit);
      ItemStack preview = configured ? this.kitManager.previewItem(kit) : null;
      ItemStack item = new ItemStack(preview != null ? preview.getType() : Material.BARRIER);
      ItemMeta meta = item.getItemMeta();
      String title = "Kit " + kit + (label != null && !label.isBlank() ? " - " + label : "");
      meta.displayName(Component.text(title, configured ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
      List<Component> lore = new ArrayList<>();
      lore.add(this.line(configured ? "Click to use this kit." : "Not set up yet - ask an admin."));
      meta.lore(lore);
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
            Map<Integer, Integer> mapping = this.slotToKit.get(player.getUniqueId());
            Integer kit = mapping == null ? null : mapping.get(event.getRawSlot());
            if (kit != null && this.kitManager.hasKit(kit)) {
               IntConsumer callback = this.callbacks.remove(player.getUniqueId());
               this.openGuis.remove(player.getUniqueId());
               this.slotToKit.remove(player.getUniqueId());
               player.closeInventory();
               if (callback != null) {
                  callback.accept(kit);
               }
            }
         }
      }
   }
}
