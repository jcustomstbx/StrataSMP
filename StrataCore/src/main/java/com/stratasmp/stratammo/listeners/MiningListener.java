package com.stratasmp.stratammo.listeners;

import com.stratasmp.stratammo.ActiveUltimates;
import com.stratasmp.stratammo.PerkCooldowns;
import com.stratasmp.stratammo.PerkSettings;
import com.stratasmp.stratammo.PlacedBlockTracker;
import com.stratasmp.stratammo.Skill;
import com.stratasmp.stratammo.XpNotifier;
import com.stratasmp.stratammo.XpValues;
import java.util.ArrayDeque;
import java.util.EnumMap;
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

public class MiningListener implements Listener {
   private static final Map<Material, Material> SMELTS_INTO = new EnumMap<>(Material.class);
   private static final Set<Material> RESONANCE_ELIGIBLE = Set.of(
      Material.COAL_ORE,
      Material.DEEPSLATE_COAL_ORE,
      Material.IRON_ORE,
      Material.DEEPSLATE_IRON_ORE,
      Material.COPPER_ORE,
      Material.DEEPSLATE_COPPER_ORE,
      Material.GOLD_ORE,
      Material.DEEPSLATE_GOLD_ORE,
      Material.REDSTONE_ORE,
      Material.DEEPSLATE_REDSTONE_ORE,
      Material.LAPIS_ORE,
      Material.DEEPSLATE_LAPIS_ORE
   );
   private final XpValues xpValues;
   private final XpNotifier notifier;
   private final PlacedBlockTracker placedTracker;
   private final boolean antiFarm;
   private final PerkSettings perks;
   private final PerkCooldowns cooldowns;
   private final ActiveUltimates activeUltimates;
   private final Plugin plugin;

   public MiningListener(
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
         int xp = this.xpValues.mining(type);
         if (xp > 0) {
            boolean wasPlaced = this.placedTracker.wasPlaced(block.getLocation());
            this.placedTracker.forget(block.getLocation());
            if (!this.antiFarm || !wasPlaced) {
               if (this.perks.enabled() && this.activeUltimates.isActive(player.getUniqueId(), Skill.MINING)) {
                  this.seismicShift(player, block, type, xp);
               } else {
                  this.notifier.award(player, Skill.MINING, xp);
                  if (this.perks.enabled()) {
                     int level = this.notifier.levelOf(player, Skill.MINING);
                     if (level >= 25) {
                        this.hasteProc(player);
                     }

                     if (level >= 50) {
                        this.blastFurnace(player, block, type);
                     }

                     if (level >= 75) {
                        this.resonance(player, block, type);
                     }
                  }
               }
            }
         }
      }
   }

   private void hasteProc(Player player) {
      if (ThreadLocalRandom.current().nextInt(100) < this.perks.miningHasteProcChance()) {
         if (this.cooldowns.tryTrigger(player.getUniqueId(), "MINING:haste", this.perks.minorBuffCooldownSeconds())) {
            int duration = ThreadLocalRandom.current().nextInt(5, 11);
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration * 20, 1));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6F, 1.4F);
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0.0, 1.0, 0.0), 6, 0.3, 0.3, 0.3);
         }
      }
   }

   private void blastFurnace(Player player, Block block, Material oreType) {
      Material smelted = SMELTS_INTO.get(oreType);
      if (smelted != null) {
         if (ThreadLocalRandom.current().nextInt(100) < this.perks.miningBlastFurnaceChance()) {
            if (this.cooldowns.tryTrigger(player.getUniqueId(), "MINING:blastfurnace", this.perks.utilityCooldownSeconds())) {
               block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(smelted, 1));
               player.playSound(player.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 0.7F, 1.2F);
               player.getWorld().spawnParticle(Particle.FLAME, block.getLocation().add(0.5, 0.5, 0.5), 8, 0.2, 0.2, 0.2);
            }
         }
      }
   }

   private void resonance(Player player, Block block, Material oreType) {
      if (RESONANCE_ELIGIBLE.contains(oreType)) {
         if (ThreadLocalRandom.current().nextInt(100) < this.perks.miningResonanceChance()) {
            if (this.cooldowns.tryTrigger(player.getUniqueId(), "MINING:resonance", this.perks.conservationCooldownSeconds())) {
               Location loc = block.getLocation();
               player.getServer().getScheduler().runTask(this.plugin, () -> {
                  if (loc.getBlock().getType() == Material.AIR) {
                     loc.getBlock().setType(oreType);
                     loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3);
                  }
               });
            }
         }
      }
   }

   private void seismicShift(Player player, Block origin, Material type, int originXp) {
      int max = this.perks.miningSeismicShiftMaxBlocks();
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
               int xp = current.equals(origin) ? originXp : this.xpValues.mining(type);
               this.notifier.award(player, Skill.MINING, xp);
            }

            broken++;

            for (int dx = -1; dx <= 1; dx++) {
               for (int dy = -1; dy <= 1; dy++) {
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

      player.getWorld().spawnParticle(Particle.EXPLOSION, origin.getLocation().add(0.5, 0.5, 0.5), 1);
      player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4F, 1.5F);
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

   static {
      SMELTS_INTO.put(Material.IRON_ORE, Material.IRON_INGOT);
      SMELTS_INTO.put(Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT);
      SMELTS_INTO.put(Material.GOLD_ORE, Material.GOLD_INGOT);
      SMELTS_INTO.put(Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT);
      SMELTS_INTO.put(Material.COPPER_ORE, Material.COPPER_INGOT);
      SMELTS_INTO.put(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT);
      SMELTS_INTO.put(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);
   }
}
