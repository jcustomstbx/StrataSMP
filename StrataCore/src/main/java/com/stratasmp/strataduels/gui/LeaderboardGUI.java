package com.stratasmp.strataduels.gui;

import com.stratasmp.strataduels.DataManager;
import com.stratasmp.strataduels.DuelPlayerData;
import com.stratasmp.strataduels.RankTier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import com.stratasmp.stratacore.StrataModule;

public class LeaderboardGUI implements Listener {
   private final StrataModule plugin;
   private final DataManager dataManager;
   private final Map<UUID, Inventory> openGuis = new HashMap<>();

   public LeaderboardGUI(StrataModule plugin, DataManager dataManager) {
      this.plugin = plugin;
      this.dataManager = dataManager;
   }

   public void open(Player viewer) {
      Inventory inventory = Bukkit.createInventory(null, 54, Component.text("Arena Duel Leaderboard"));
      List<DuelPlayerData> top = this.dataManager.getTopN(45);
      ConfigurationSection thresholds = this.plugin.getConfig().getConfigurationSection("rank-tiers");
      int slot = 0;
      int rank = 1;

      for (DuelPlayerData data : top) {
         inventory.setItem(slot, this.buildRow(data, rank, thresholds));
         slot++;
         rank++;
      }

      for (int i = top.size(); i < 54; i++) {
         inventory.setItem(i, this.filler());
      }

      this.openGuis.put(viewer.getUniqueId(), inventory);
      viewer.openInventory(inventory);
   }

   private ItemStack buildRow(DuelPlayerData data, int rank, ConfigurationSection thresholds) {
      OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(data.uuid);
      ItemStack item = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta meta = (SkullMeta)item.getItemMeta();
      meta.setOwningPlayer(offlinePlayer);
      RankTier tier = RankTier.forElo(data.seasonElo, thresholds);
      meta.displayName(Component.text("#" + rank + " " + data.lastKnownName, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
      List<Component> lore = new ArrayList<>();
      lore.add(this.line(tier.displayName() + " - " + Math.round(data.seasonElo) + " ELO"));
      lore.add(this.line(data.seasonWins + "W / " + data.seasonLosses + "L"));
      lore.add(this.line("Win streak: " + data.seasonWinStreak + " (best " + data.seasonBestStreak + ")"));
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
         }
      }
   }
}
