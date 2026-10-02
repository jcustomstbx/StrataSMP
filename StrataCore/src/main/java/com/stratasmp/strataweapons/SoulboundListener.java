package com.stratasmp.strataweapons;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

/**
 * Soulbound skinned items (any weapon or armour piece a skin has been applied to) can't be dropped,
 * placed in any container, hung in an item frame or put on an armour stand - only carried. On death
 * they drop as the plain, unskinned item (see onDeath), so a skin is never handed to the killer. That
 * also closes last season's dupe, where a weapon left in a chest survived independently of the copy
 * that death was tracking and dying with one both in a chest and on the player made two live copies.
 */
public class SoulboundListener implements Listener {
   private static final Set<InventoryType> ITEM_STATIONS =
      EnumSet.of(InventoryType.ENCHANTING, InventoryType.ANVIL, InventoryType.GRINDSTONE, InventoryType.SMITHING);

   private final WeaponCatalog catalog;

   public SoulboundListener(WeaponCatalog catalog) {
      this.catalog = catalog;
   }

   /**
    * A skin belongs to whoever bought it, so a skinned item that drops on death drops as the plain item
    * (same material and enchantments, no skin, name or soulbound tag). The killer gets the gear but never
    * the look, and the copy can't come back at respawn. With keepInventory nothing is in the drops, so
    * the skin stays on the player.
    */
   @EventHandler
   public void onDeath(PlayerDeathEvent event) {
      List<ItemStack> drops = event.getDrops();
      for (int i = 0; i < drops.size(); i++) {
         ItemStack item = drops.get(i);
         if (this.catalog.holdsSoulbound(item)) {
            this.catalog.stripSkins(item);
            drops.set(i, item);
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onDrop(PlayerDropItemEvent event) {
      if (this.catalog.holdsSoulbound(event.getItemDrop().getItemStack())) {
         event.setCancelled(true);
         event.getPlayer().sendMessage(Component.text("That weapon is soulbound - it can't be dropped.", NamedTextColor.RED));
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onAuctionSell(PlayerCommandPreprocessEvent event) {
      String[] words = event.getMessage().substring(1).trim().split("\\s+");
      if (words.length < 2 || !words[1].equalsIgnoreCase("sell")) {
         return;
      }
      // resolved through the command map so aliases and the plugin:label form are caught too
      Command command = Bukkit.getCommandMap().getCommand(words[0].toLowerCase());
      if (!(command instanceof PluginCommand pluginCommand)
         || !pluginCommand.getPlugin().getName().equals("StrataCore")
         || !command.getName().equals("ah")) {
         return;
      }
      Player player = event.getPlayer();
      if (this.catalog.holdsSoulbound(player.getInventory().getItemInMainHand())) {
         event.setCancelled(true);
         player.sendMessage(Component.text("That weapon is soulbound - it can't be sold on the auction house.", NamedTextColor.RED));
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onClick(InventoryClickEvent event) {
      // bundles work inside the player's own inventory, so this has to be checked before the
      // "only the player's inventory is open" early return below
      if (bundleMeetsSoulbound(event.getCursor(), event.getCurrentItem())) {
         this.block(event, event.getWhoClicked());
         return;
      }

      // only the player's own inventory (and crafting-adjacent slots within it) is a safe
      // destination - anything else open alongside it is a container of some kind
      if (isSafeTop(event.getView().getTopInventory())) {
         return;
      }

      ClickType click = event.getClick();
      if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
         // shift-clicking from the player's own (bottom) inventory sends the item into the
         // container above it
         if (event.getClickedInventory() != null
            && event.getClickedInventory().getHolder() instanceof Player
            && this.catalog.holdsSoulbound(event.getCurrentItem())) {
            this.block(event, event.getWhoClicked());
         }
      } else if (click == ClickType.NUMBER_KEY) {
         // swaps the clicked container slot with a hotbar slot - block if the incoming hotbar
         // item is soulbound
         ItemStack hotbarItem = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
         if (this.catalog.holdsSoulbound(hotbarItem)) {
            this.block(event, event.getWhoClicked());
         }
      } else if (event.getClickedInventory() != null && !(event.getClickedInventory().getHolder() instanceof Player)) {
         // placing straight from the cursor into a container slot
         if (this.catalog.holdsSoulbound(event.getCursor())) {
            this.block(event, event.getWhoClicked());
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onDrag(InventoryDragEvent event) {
      if (isSafeTop(event.getView().getTopInventory())) {
         return;
      }
      int topSize = event.getView().getTopInventory().getSize();
      boolean touchesContainer = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
      if (touchesContainer && this.catalog.holdsSoulbound(event.getOldCursor())) {
         event.setCancelled(true);
         if (event.getWhoClicked() instanceof Player player) {
            player.sendMessage(Component.text("That weapon is soulbound - it can't be placed in a container.", NamedTextColor.RED));
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onItemFrame(PlayerInteractEntityEvent event) {
      if (event.getRightClicked() instanceof ItemFrame) {
         Player player = event.getPlayer();
         ItemStack hand = player.getInventory().getItemInMainHand();
         ItemStack offhand = player.getInventory().getItemInOffHand();
         if (this.catalog.holdsSoulbound(hand) || this.catalog.holdsSoulbound(offhand)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("That weapon is soulbound - it can't go in an item frame.", NamedTextColor.RED));
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onArmorStand(PlayerArmorStandManipulateEvent event) {
      // hanging a skinned piece on a stand would let someone else pick it up
      if (this.catalog.holdsSoulbound(event.getPlayerItem())) {
         event.setCancelled(true);
         event.getPlayer().sendMessage(Component.text("That item is soulbound - it can't go on an armour stand.", NamedTextColor.RED));
      }
   }

   /**
    * The player's own inventory, or a station that hands its items back when it closes. Those have to stay
    * usable or a skinned weapon can't be enchanted, repaired or upgraded.
    */
   private static boolean isSafeTop(Inventory top) {
      return top.getHolder() instanceof Player || ITEM_STATIONS.contains(top.getType());
   }

   private boolean bundleMeetsSoulbound(ItemStack cursor, ItemStack clicked) {
      return (isBundle(cursor) && this.catalog.isSoulbound(clicked)) || (isBundle(clicked) && this.catalog.isSoulbound(cursor));
   }

   private static boolean isBundle(ItemStack item) {
      return item != null && item.hasItemMeta() && item.getItemMeta() instanceof BundleMeta;
   }

   private void block(InventoryClickEvent event, org.bukkit.entity.HumanEntity who) {
      event.setCancelled(true);
      if (who instanceof Player player) {
         player.sendMessage(Component.text("That weapon is soulbound - it can't be stored in a container or bundle.", NamedTextColor.RED));
      }
   }
}
