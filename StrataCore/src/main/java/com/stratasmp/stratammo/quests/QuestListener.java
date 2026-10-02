package com.stratasmp.stratammo.quests;

import com.stratasmp.stratammo.PlacedBlockTracker;
import com.stratasmp.stratammo.XpNotifier;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class QuestListener implements Listener {
   private final QuestManager quests;
   private final XpNotifier notifier;
   private final PlacedBlockTracker placed;

   public QuestListener(QuestManager quests, XpNotifier notifier, PlacedBlockTracker placed) {
      this.quests = quests;
      this.notifier = notifier;
      this.placed = placed;
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      this.quests.load(event.getPlayer());
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.quests.unload(event.getPlayer());
   }

   // LOW so we read the placed-block tracker before the skill listeners forget the block
   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
   public void onBreak(BlockBreakEvent event) {
      Player player = event.getPlayer();
      if (player.getGameMode() == GameMode.CREATIVE || !this.notifier.accepts(player)) return;
      Block block = event.getBlock();
      Material type = block.getType();
      if (block.getBlockData() instanceof Ageable ageable) {
         if (ageable.getAge() >= ageable.getMaximumAge()) {
            this.quests.progress(player, ObjectiveType.HARVEST_CROP, type.name(), 1);
         }
         return;
      }
      if (this.placed.wasPlaced(block.getLocation())) return;
      if (Tag.LOGS.isTagged(type)) {
         this.quests.progress(player, ObjectiveType.CHOP_LOG, type.name(), 1);
      } else {
         this.quests.progress(player, ObjectiveType.MINE_BLOCK, type.name(), 1);
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onKill(EntityDeathEvent event) {
      Player killer = event.getEntity().getKiller();
      if (killer == null || !this.notifier.accepts(killer)) return;
      this.quests.progress(killer, ObjectiveType.KILL_MOB, event.getEntityType().name(), 1);
   }

   @EventHandler(ignoreCancelled = true)
   public void onFish(PlayerFishEvent event) {
      if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !this.notifier.accepts(event.getPlayer())) return;
      String key = event.getCaught() instanceof Item item ? item.getItemStack().getType().name() : "";
      this.quests.progress(event.getPlayer(), ObjectiveType.CATCH_FISH, key, 1);
   }

   @EventHandler(ignoreCancelled = true)
   public void onEnchant(EnchantItemEvent event) {
      if (!this.notifier.accepts(event.getEnchanter())) return;
      this.quests.progress(event.getEnchanter(), ObjectiveType.ENCHANT_ITEM, event.getItem().getType().name(), 1);
   }
}
