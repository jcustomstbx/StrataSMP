package com.stratasmp.strataweapons;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import com.stratasmp.stratacore.StrataModule;
import org.jetbrains.annotations.NotNull;

/**
 * /skinpurge <skin> removes every copy of a skin from the server: online players now, auction listings,
 * dropped items and containers in loaded chunks, and everyone else the next time they are in the SMP.
 * Players can't all be reached at once because their SMP inventory is stashed by Multiverse-Inventories while they
 * stand in the hub (and offline players' data files aren't touched), so each of them is queued and swept once,
 * the first time they are in a sweep world after the purge. Skins can still be applied again afterwards.
 */
public class SkinPurge implements Listener, TabExecutor {
   private static final List<String> DEFAULT_SWEEP_WORLDS = List.of("world", "world_nether", "world_the_end");
   private static final int CHUNKS_PER_TICK = 40;
   private static final long SETTLE_DELAY_TICKS = 20L;

   private final StrataModule plugin;
   private final WeaponCatalog catalog;
   private final File file;
   private final Set<String> keys = new HashSet<>();
   private final Map<String, Set<UUID>> pending = new HashMap<>();
   private final Map<String, Set<UUID>> restore = new HashMap<>();

   public SkinPurge(StrataModule plugin, WeaponCatalog catalog) {
      this.plugin = plugin;
      this.catalog = catalog;
      this.file = new File(plugin.getDataFolder(), "purge.yml");
      load();
   }

   private void load() {
      if (!file.exists()) {
         return;
      }
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
      keys.addAll(yaml.getStringList("keys"));
      readIds(yaml.getConfigurationSection("pending"), pending);
      readIds(yaml.getConfigurationSection("restore"), restore);
   }

   private static void readIds(ConfigurationSection section, Map<String, Set<UUID>> into) {
      if (section == null) {
         return;
      }
      for (String key : section.getKeys(false)) {
         Set<UUID> ids = new HashSet<>();
         for (String raw : section.getStringList(key)) {
            try {
               ids.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
               // hand-edited garbage in the file - skip it
            }
         }
         into.put(key, ids);
      }
   }

   private void save() {
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.set("keys", new ArrayList<>(keys));
      pending.forEach((key, ids) -> yaml.set("pending." + key, ids.stream().map(UUID::toString).toList()));
      restore.forEach((key, ids) -> yaml.set("restore." + key, ids.stream().map(UUID::toString).toList()));
      try {
         plugin.getDataFolder().mkdirs();
         yaml.save(file);
      } catch (IOException e) {
         plugin.getLogger().warning("Couldn't save purge.yml: " + e.getMessage());
      }
   }

   private List<String> sweepWorlds() {
      List<String> configured = plugin.getConfig().getStringList("purge.sweep-worlds");
      return configured.isEmpty() ? DEFAULT_SWEEP_WORLDS : configured;
   }

   @Override
   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
      boolean restoring = args.length == 3 && args[1].equalsIgnoreCase("restore");
      if ((args.length != 1 && !restoring) || !catalog.has(args[0])) {
         sender.sendMessage("Usage: /skinpurge <skin> | /skinpurge <skin> restore <player>  (known skins: "
            + String.join(", ", catalog.keys()) + ")");
         return true;
      }
      String key = args[0].toLowerCase();
      if (restoring) {
         queueRestore(sender, key, args[2]);
         return true;
      }
      keys.add(key);
      Set<UUID> queue = pending.computeIfAbsent(key, k -> new HashSet<>());

      int online = 0;
      for (Player player : Bukkit.getOnlinePlayers()) {
         online += sweepPlayer(player, key);
         if (!sweepWorlds().contains(player.getWorld().getName())) {
            queue.add(player.getUniqueId());
         }
      }
      for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
         if (offline.getPlayer() == null) {
            queue.add(offline.getUniqueId());
         }
      }
      int listings = purgeAuctions(key);
      int loose = purgeLoose(key);
      save();

      String head = "Purging '" + key + "': " + online + " from online players' inventories, " + listings
         + " auction listing(s), " + loose + " dropped/framed. " + queue.size() + " player(s) queued for their next SMP login.";
      sender.sendMessage(head);
      plugin.getLogger().info(head);
      sweepContainers(sender, key);
      return true;
   }

   /** Hands one purged skin back to a player who legitimately owns it, right after their own purge sweep. */
   private void queueRestore(CommandSender sender, String key, String name) {
      com.stratasmp.stratacore.NameLookup.resolve(plugin, name, (uuid, resolved) -> {
         restore.computeIfAbsent(key, k -> new HashSet<>()).add(uuid);
         save();
         Player online = Bukkit.getPlayer(uuid);
         if (online != null) {
            settle(online);
         }
         sender.sendMessage("Queued a " + catalog.displayNameOf(key) + " for " + resolved + "; they get it once they're in the SMP and any pending purge has run.");
      }, () -> sender.sendMessage("No player called '" + name + "' was found."));
   }

   @Override
   public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
      return args.length == 1 ? List.copyOf(catalog.keys()) : List.of();
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      settleLater(event.getPlayer());
   }

   @EventHandler
   public void onWorldChange(PlayerChangedWorldEvent event) {
      settleLater(event.getPlayer());
   }

   @EventHandler
   public void onEntitiesLoad(EntitiesLoadEvent event) {
      if (keys.isEmpty()) {
         return;
      }
      for (Entity entity : event.getEntities()) {
         for (String key : keys) {
            purgeEntity(entity, key);
         }
      }
   }

   private void settleLater(Player player) {
      if (pending.values().stream().allMatch(Set::isEmpty) && restore.values().stream().allMatch(Set::isEmpty)) {
         return;
      }
      // Multiverse-Inventories swaps the inventory in around the world change, so wait for it to finish
      Bukkit.getScheduler().runTaskLater(plugin, () -> settle(player), SETTLE_DELAY_TICKS);
   }

   private void settle(Player player) {
      if (!player.isOnline() || !sweepWorlds().contains(player.getWorld().getName())) {
         return;
      }
      boolean changed = false;
      for (Map.Entry<String, Set<UUID>> entry : pending.entrySet()) {
         if (!entry.getValue().remove(player.getUniqueId())) {
            continue;
         }
         changed = true;
         int removed = sweepPlayer(player, entry.getKey());
         if (removed > 0) {
            plugin.getLogger().info("Purged " + removed + " '" + entry.getKey() + "' from " + player.getName() + " on login.");
            player.sendMessage(Component.text("Your " + catalog.displayNameOf(entry.getKey())
               + " weapon was removed - if you own the skin you can apply it again from /skins.", NamedTextColor.RED));
         }
      }
      for (Map.Entry<String, Set<UUID>> entry : restore.entrySet()) {
         if (entry.getValue().remove(player.getUniqueId())) {
            changed = true;
            giveBack(player, entry.getKey());
         }
      }
      if (changed) {
         save();
      }
   }

   private void giveBack(Player player, String key) {
      ItemStack sword = new ItemStack(Material.WOODEN_SWORD);
      catalog.applySkinTo(sword, key);
      player.getInventory().addItem(sword).values()
         .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
      plugin.getLogger().info("Gave " + player.getName() + " a " + key + " sword back.");
      player.sendMessage(Component.text("Your " + catalog.displayNameOf(key) + " has been returned to you.", NamedTextColor.GREEN));
   }

   private boolean matches(ItemStack item, String key) {
      return item != null && item.hasItemMeta() && key.equals(catalog.weaponKeyOf(item));
   }

   private int sweepPlayer(Player player, String key) {
      int removed = stripInventory(player.getInventory(), key) + stripInventory(player.getEnderChest(), key);
      ItemStack cursor = player.getItemOnCursor();
      if (matches(cursor, key)) {
         removed += cursor.getAmount();
         player.setItemOnCursor(null);
      } else if (cursor != null && cursor.getType() != Material.AIR) {
         ItemStack copy = cursor.clone();
         int nested = stripNested(copy, key);
         if (nested > 0) {
            player.setItemOnCursor(copy);
            removed += nested;
         }
      }
      return removed;
   }

   private int stripInventory(Inventory inventory, String key) {
      ItemStack[] contents = inventory.getContents();
      int removed = 0;
      for (int i = 0; i < contents.length; i++) {
         ItemStack item = contents[i];
         if (item == null) {
            continue;
         }
         if (matches(item, key)) {
            removed += item.getAmount();
            contents[i] = null;
         } else {
            removed += stripNested(item, key);
         }
      }
      if (removed > 0) {
         inventory.setContents(contents);
      }
      return removed;
   }

   /** Takes the skin out of a bundle or shulker box item, keeping the rest of what is inside. */
   private int stripNested(ItemStack item, String key) {
      if (!item.hasItemMeta()) {
         return 0;
      }
      ItemMeta meta = item.getItemMeta();
      if (meta instanceof BundleMeta bundle) {
         List<ItemStack> kept = new ArrayList<>();
         int removed = 0;
         for (ItemStack inner : bundle.getItems()) {
            if (matches(inner, key)) {
               removed += inner.getAmount();
            } else {
               kept.add(inner);
            }
         }
         if (removed > 0) {
            bundle.setItems(kept);
            item.setItemMeta(bundle);
         }
         return removed;
      }
      if (meta instanceof BlockStateMeta state && state.hasBlockState() && state.getBlockState() instanceof ShulkerBox box) {
         int removed = stripInventory(box.getInventory(), key);
         if (removed > 0) {
            state.setBlockState(box);
            item.setItemMeta(state);
         }
         return removed;
      }
      return 0;
   }

   private int purgeEntity(Entity entity, String key) {
      if (entity instanceof Item dropped) {
         ItemStack stack = dropped.getItemStack();
         if (matches(stack, key)) {
            dropped.remove();
            return stack.getAmount();
         }
         ItemStack copy = stack.clone();
         int nested = stripNested(copy, key);
         if (nested > 0) {
            dropped.setItemStack(copy);
         }
         return nested;
      }
      if (entity instanceof ItemFrame frame && matches(frame.getItem(), key)) {
         frame.setItem(null);
         return 1;
      }
      return 0;
   }

   private int purgeLoose(String key) {
      int removed = 0;
      for (World world : Bukkit.getWorlds()) {
         for (Entity entity : world.getEntitiesByClasses(Item.class, ItemFrame.class)) {
            removed += purgeEntity(entity, key);
         }
      }
      return removed;
   }

   private void sweepContainers(CommandSender sender, String key) {
      List<Chunk> chunks = new ArrayList<>();
      for (World world : Bukkit.getWorlds()) {
         chunks.addAll(List.of(world.getLoadedChunks()));
      }
      AtomicInteger next = new AtomicInteger();
      AtomicInteger removed = new AtomicInteger();
      Bukkit.getScheduler().runTaskTimer(plugin, task -> {
         int end = Math.min(next.get() + CHUNKS_PER_TICK, chunks.size());
         for (int i = next.get(); i < end; i++) {
            Chunk chunk = chunks.get(i);
            if (!chunk.isLoaded()) {
               continue;
            }
            for (BlockState state : chunk.getTileEntities(false)) {
               if (state instanceof Container container) {
                  removed.addAndGet(stripInventory(container.getInventory(), key));
               }
            }
         }
         next.set(end);
         if (end >= chunks.size()) {
            task.cancel();
            String done = "Purge of '" + key + "': " + removed.get() + " taken out of containers across " + chunks.size() + " loaded chunk(s).";
            sender.sendMessage(done);
            plugin.getLogger().info(done);
         }
      }, 1L, 1L);
   }

   private int purgeAuctions(String key) {
      com.stratasmp.strataeconomy.StrataEconomy economy = plugin.core().module(com.stratasmp.strataeconomy.StrataEconomy.class);
      if (economy == null || !economy.isEnabled()) {
         return 0;
      }
      com.stratasmp.strataeconomy.api.Auctions store = economy.auctions();
      List<com.stratasmp.strataeconomy.api.Auctions.Listing> listings = store.all();
      int removed = 0;
      for (com.stratasmp.strataeconomy.api.Auctions.Listing listing : listings) {
         ItemStack stack = listing.item();
         if (matches(stack, key) || stripNested(stack.clone(), key) > 0) {
            if (store.adminRemove(listing.id())) {
               removed++;
            }
         }
      }
      plugin.getLogger().info("Auction house: checked " + listings.size() + " listing(s), removed " + removed + ".");
      return removed;
   }
}
