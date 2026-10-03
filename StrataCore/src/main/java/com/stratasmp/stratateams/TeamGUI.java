package com.stratasmp.stratateams;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public class TeamGUI implements Listener {
   private static final int INVITE_SLOT = 19;
   private static final int FF_SLOT = 20;
   private static final int SET_HOME_SLOT = 21;
   private static final int TEAM_HOME_SLOT = 22;
   private static final int LEAVE_DISBAND_SLOT = 26;
   private final TeamManager teams;
   private final HomeTeleporter teleporter;
   private final Map<UUID, Inventory> openGuis = new HashMap<>();
   private final Map<UUID, Map<Integer, UUID>> memberSlots = new HashMap<>();

   public TeamGUI(TeamManager teams, HomeTeleporter teleporter) {
      this.teams = teams;
      this.teleporter = teleporter;
   }

   public void open(Player player) {
      Team team = this.teams.getTeam(player.getUniqueId());
      Inventory inventory = team == null ? this.buildNoTeamGui(player) : this.buildTeamGui(player, team);
      this.openGuis.put(player.getUniqueId(), inventory);
      player.openInventory(inventory);
   }

   private Inventory buildNoTeamGui(Player player) {
      Inventory inventory = Bukkit.createInventory(null, 9, Component.text("Team"));
      ItemStack info = new ItemStack(Material.PAPER);
      ItemMeta meta = info.getItemMeta();
      meta.displayName(Component.text("You're not in a team", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
      meta.lore(List.of((TextComponent)Component.text("Use /myteam create <name>", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
      info.setItemMeta(meta);
      inventory.setItem(4, info);
      return inventory;
   }

   private Inventory buildTeamGui(Player player, Team team) {
      Inventory inventory = Bukkit.createInventory(null, 27, Component.text("Team: " + team.name));
      boolean isOwner = team.owner.equals(player.getUniqueId());
      Map<Integer, UUID> slotMap = new HashMap<>();
      int slot = 0;

      for (UUID memberUuid : team.members) {
         if (slot >= 18) {
            break;
         }

         inventory.setItem(slot, this.buildMemberHead(team, memberUuid));
         slotMap.put(slot, memberUuid);
         slot++;
      }

      this.memberSlots.put(player.getUniqueId(), slotMap);
      inventory.setItem(19, this.button(Material.PAPER, "Invite a player", "Use /myteam invite <player>"));
      inventory.setItem(20, this.ffButton(team, isOwner));
      inventory.setItem(21, isOwner ? this.button(Material.RED_BED, "Set Team Home", "Click to set the home to where you're standing") : this.filler());
      inventory.setItem(
         22,
         team.home == null
            ? this.button(Material.GRAY_DYE, "No Team Home Set", "Ask the owner to set one")
            : this.button(Material.COMPASS, "Team Home", "Click to teleport")
      );
      inventory.setItem(
         26,
         isOwner
            ? this.button(Material.BARRIER, "Disband Team", "Click to permanently disband the team")
            : this.button(Material.OAK_DOOR, "Leave Team", "Click to leave the team")
      );

      for (int i = 18; i < 27; i++) {
         if (inventory.getItem(i) == null) {
            inventory.setItem(i, this.filler());
         }
      }

      return inventory;
   }

   private ItemStack buildMemberHead(Team team, UUID memberUuid) {
      OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(memberUuid);
      ItemStack item = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta meta = (SkullMeta)item.getItemMeta();
      meta.setOwningPlayer(offlinePlayer);
      String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";
      boolean isTeamOwner = team.owner.equals(memberUuid);
      meta.displayName(Component.text(name + (isTeamOwner ? " (Owner)" : ""), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
      if (isTeamOwner) {
         meta.lore(List.of());
      } else {
         meta.lore(List.of((TextComponent)Component.text("Owner can click to remove", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
      }

      item.setItemMeta(meta);
      return item;
   }

   private ItemStack ffButton(Team team, boolean isOwner) {
      Material material = team.friendlyFire ? Material.RED_CONCRETE : Material.LIME_CONCRETE;
      String status = team.friendlyFire ? "ON" : "OFF";
      List<String> lore = new ArrayList<>();
      lore.add("Teammates " + (team.friendlyFire ? "CAN" : "can't") + " hurt each other or each other's pets");
      if (isOwner) {
         lore.add("Click to toggle");
      }

      return this.buttonWithLore(material, "Friendly Fire: " + status, lore);
   }

   private ItemStack button(Material material, String name, String loreLine) {
      return this.buttonWithLore(material, name, List.of(loreLine));
   }

   private ItemStack buttonWithLore(Material material, String name, List<String> loreLines) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
      List<Component> lore = new ArrayList<>();

      for (String line : loreLines) {
         lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
      }

      meta.lore(lore);
      item.setItemMeta(meta);
      return item;
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
            int slot = event.getRawSlot();
            if (slot >= 0 && slot < inventory.getSize()) {
               Team team = this.teams.getTeam(player.getUniqueId());
               if (team != null) {
                  boolean isOwner = team.owner.equals(player.getUniqueId());
                  Map<Integer, UUID> slotMap = this.memberSlots.getOrDefault(player.getUniqueId(), Map.of());
                  if (slotMap.containsKey(slot)) {
                     if (isOwner) {
                        UUID targetUuid = slotMap.get(slot);
                        if (!targetUuid.equals(player.getUniqueId())) {
                           OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
                           String error = this.teams.kick(player, targetUuid);
                           String shown = target.getName() == null ? targetUuid.toString().substring(0, 8) : target.getName();
                           player.sendMessage(error != null ? error : shown + " was removed from the team.");
                           this.open(player);
                        }
                     }
                  } else if (slot == 20) {
                     if (isOwner) {
                        this.teams.toggleFriendlyFire(player);
                        this.open(player);
                     }
                  } else if (slot == 21) {
                     if (isOwner) {
                        this.teams.setHome(player);
                        player.sendMessage("Team home set to your current location.");
                        this.open(player);
                     }
                  } else if (slot == 22) {
                     if (team.home != null) {
                        player.closeInventory();
                        this.teleporter.startTeleport(player);
                     }
                  } else {
                     if (slot == 26) {
                        String error = isOwner ? this.teams.disband(player) : this.teams.leave(player);
                        player.closeInventory();
                        player.sendMessage(error != null ? error : (isOwner ? "Team disbanded." : "You left the team."));
                     }
                  }
               }
            }
         }
      }
   }
}
