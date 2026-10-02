package com.stratasmp.stratammo.listeners;

import com.stratasmp.stratammo.ActiveUltimates;
import com.stratasmp.stratammo.PerkCooldowns;
import com.stratasmp.stratammo.PerkSettings;
import com.stratasmp.stratammo.PlacedBlockTracker;
import com.stratasmp.stratammo.Skill;
import com.stratasmp.stratammo.XpNotifier;
import com.stratasmp.stratammo.XpValues;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class WoodcuttingListener implements Listener {
   private final XpValues xpValues;
   private final XpNotifier notifier;
   private final PlacedBlockTracker placedTracker;
   private final boolean antiFarm;
   private final PerkSettings perks;
   private final PerkCooldowns cooldowns;
   private final ActiveUltimates activeUltimates;
   private final Plugin plugin;

   public WoodcuttingListener(
      XpValues xpValues,
      XpNotifier notifier,
      PlacedBlockTracker placedTracker,
      boolean antiFarm,
      PerkSettings perks,
      PerkCooldowns cooldowns,
      ActiveUltimates activeUltimates,
      Plugin plugin
   ) {
      this.xpValues = xpValues;
      this.notifier = notifier;
      this.placedTracker = placedTracker;
      this.antiFarm = antiFarm;
      this.perks = perks;
      this.cooldowns = cooldowns;
      this.activeUltimates = activeUltimates;
      this.plugin = plugin;
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onBreak(BlockBreakEvent event) {
      Player player = event.getPlayer();
      if (!this.notifier.accepts(player)) return;
      if (player.getGameMode() != GameMode.CREATIVE) {
         Block block = event.getBlock();
         Material type = block.getType();
         int xp = this.xpValues.woodcutting(type);
         if (xp > 0) {
            boolean wasPlaced = this.placedTracker.wasPlaced(block.getLocation());
            this.placedTracker.forget(block.getLocation());
            if (!this.antiFarm || !wasPlaced) {
               if (this.perks.enabled() && this.activeUltimates.isActive(player.getUniqueId(), Skill.WOODCUTTING)) {
                  this.timberFall(player, block, type, xp);
               } else {
                  this.notifier.award(player, Skill.WOODCUTTING, xp);
                  if (this.perks.enabled()) {
                     int level = this.notifier.levelOf(player, Skill.WOODCUTTING);
                     if (level >= 25) {
                        this.lumberjacksPace(player);
                     }

                     if (level >= 50) {
                        this.resinExtraction(player, block);
                     }

                     if (level >= 75) {
                        this.rootSystem(player, block, type);
                     }
                  }
               }
            }
         }
      }
   }

   private void lumberjacksPace(Player player) {
      if (ThreadLocalRandom.current().nextInt(100) < this.perks.woodcuttingLumberjackPaceChance()) {
         if (this.cooldowns.tryTrigger(player.getUniqueId(), "WOODCUTTING:pace", this.perks.minorBuffCooldownSeconds())) {
            int duration = ThreadLocalRandom.current().nextInt(5, 11);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration * 20, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration * 20, 0));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6F, 1.4F);
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0.0, 1.0, 0.0), 6, 0.3, 0.3, 0.3);
         }
      }
   }

   private void resinExtraction(Player player, Block block) {
      if (ThreadLocalRandom.current().nextInt(100) < this.perks.woodcuttingResinExtractionChance()) {
         if (this.cooldowns.tryTrigger(player.getUniqueId(), "WOODCUTTING:resin", this.perks.utilityCooldownSeconds())) {
            Material bonus = ThreadLocalRandom.current().nextBoolean() ? Material.STICK : Material.APPLE;
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(bonus, 1));
         }
      }
   }

   private void rootSystem(Player player, Block block, Material logType) {
      if (ThreadLocalRandom.current().nextInt(100) < this.perks.woodcuttingRootSystemChance()) {
         if (this.cooldowns.tryTrigger(player.getUniqueId(), "WOODCUTTING:root", this.perks.conservationCooldownSeconds())) {
            Location loc = block.getLocation();
            player.getServer().getScheduler().runTask(this.plugin, () -> {
               if (loc.getBlock().getType() == Material.AIR) {
                  loc.getBlock().setType(logType);
                  loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3);
               }
            });
         }
      }
   }

   private void timberFall(Player player, Block origin, Material type, int originXp) {
      int max = this.perks.woodcuttingTimberFallMaxLogs();
      Set<Block> visited = new HashSet<>();
      ArrayDeque<Block> queue = new ArrayDeque<>();
      queue.add(origin);
      visited.add(origin);
      int broken = 0;

      while (!queue.isEmpty() && broken < max) {
         Block current = queue.poll();
         if (current.getType() == type) {
            boolean placed = this.placedTracker.wasPlaced(current.getLocation());
            this.placedTracker.forget(current.getLocation());
            if (this.antiFarm && placed) {
               current.setType(Material.AIR);
            } else {
               this.harvest(player, current);
               int xp = current.equals(origin) ? originXp : this.xpValues.woodcutting(type);
               this.notifier.award(player, Skill.WOODCUTTING, xp);
            }

            broken++;

            for (int dx = -1; dx <= 1; dx++) {
               for (int dy = -1; dy <= 2; dy++) {
                  for (int dz = -1; dz <= 1; dz++) {
                     if (dx != 0 || dy != 0 || dz != 0) {
                        Block neighbor = current.getRelative(dx, dy, dz);
                        if (!visited.contains(neighbor)) {
                           visited.add(neighbor);
                           if (neighbor.getType() == type) {
                              queue.add(neighbor);
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, origin.getLocation().add(0.5, 0.5, 0.5), 3);
      player.playSound(player.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.6F, 0.8F);
   }

   private void harvest(Player player, Block block) {
      ItemStack tool = player.getInventory().getItemInMainHand();

      for (ItemStack drop : block.getDrops(tool, player)) {
         Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack[]{drop});

         for (ItemStack leftover : overflow.values()) {
            block.getWorld().dropItemNaturally(block.getLocation(), leftover);
         }
      }

      block.setType(Material.AIR);
   }
}
