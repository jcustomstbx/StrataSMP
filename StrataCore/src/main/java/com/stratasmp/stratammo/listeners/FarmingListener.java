package com.stratasmp.stratammo.listeners;

import com.stratasmp.stratammo.ActiveUltimates;
import com.stratasmp.stratammo.PerkCooldowns;
import com.stratasmp.stratammo.PerkSettings;
import com.stratasmp.stratammo.Skill;
import com.stratasmp.stratammo.XpNotifier;
import com.stratasmp.stratammo.XpValues;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class FarmingListener implements Listener {
   private static final Set<Material> AGE_GATED = Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART);
   private final XpValues xpValues;
   private final XpNotifier notifier;
   private final PerkSettings perks;
   private final PerkCooldowns cooldowns;
   private final ActiveUltimates activeUltimates;
   private final Plugin plugin;

   public FarmingListener(XpValues xpValues, XpNotifier notifier, PerkSettings perks, PerkCooldowns cooldowns, ActiveUltimates activeUltimates, Plugin plugin) {
      this.xpValues = xpValues;
      this.notifier = notifier;
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
         int xp = this.xpValues.farming(type);
         if (xp > 0) {
            boolean ageGated = AGE_GATED.contains(type);
            if (ageGated) {
               if (!(block.getBlockData() instanceof Ageable ageable)) {
                  return;
               }

               if (ageable.getAge() < ageable.getMaximumAge()) {
                  return;
               }
            }

            this.notifier.award(player, Skill.FARMING, xp);
            if (this.perks.enabled() && ageGated) {
               int level = this.notifier.levelOf(player, Skill.FARMING);
               if (level >= 25) {
                  this.greenThumb(player, block, type);
               }

               if (level >= 50) {
                  this.bountifulHarvest(player, xp);
               }

               if (level >= 75) {
                  this.fertileSoil(player, block);
               }
            }
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onInteract(PlayerInteractEvent event) {
      if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
         if (this.perks.enabled()) {
            Player player = event.getPlayer();
            if (!this.notifier.accepts(player)) return;
            if (this.activeUltimates.isActive(player.getUniqueId(), Skill.FARMING)) {
               Block clicked = event.getClickedBlock();
               if (clicked != null && AGE_GATED.contains(clicked.getType())) {
                  event.setCancelled(true);
                  this.naturesWrath(player, clicked);
               }
            }
         }
      }
   }

   private void greenThumb(Player player, Block block, Material cropType) {
      if (ThreadLocalRandom.current().nextInt(100) < this.perks.farmingGreenThumbChance()) {
         if (this.cooldowns.tryTrigger(player.getUniqueId(), "FARMING:greenthumb", this.perks.minorBuffCooldownSeconds())) {
            this.dropExtra(player, block);
            this.replant(block.getLocation(), cropType);
         }
      }
   }

   private void bountifulHarvest(Player player, int baseXp) {
      if (ThreadLocalRandom.current().nextInt(100) < this.perks.farmingBountifulHarvestChance()) {
         if (this.cooldowns.tryTrigger(player.getUniqueId(), "FARMING:bountiful", this.perks.utilityCooldownSeconds())) {
            int bonus = Math.max(1, baseXp * this.perks.farmingBountifulHarvestXpBonusPercent() / 100);
            this.notifier.award(player, Skill.FARMING, bonus);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.2F);
         }
      }
   }

   private void fertileSoil(Player player, Block harvested) {
      if (ThreadLocalRandom.current().nextInt(100) < this.perks.farmingFertileSoilChance()) {
         if (this.cooldowns.tryTrigger(player.getUniqueId(), "FARMING:fertilesoil", this.perks.conservationCooldownSeconds())) {
            for (int dx = -1; dx <= 1; dx++) {
               for (int dz = -1; dz <= 1; dz++) {
                  if (dx != 0 || dz != 0) {
                     Block neighbor = harvested.getRelative(dx, 0, dz);
                     if (AGE_GATED.contains(neighbor.getType())
                        && neighbor.getBlockData() instanceof Ageable ageable
                        && ageable.getAge() < ageable.getMaximumAge()) {
                        ageable.setAge(ageable.getMaximumAge());
                        neighbor.setBlockData(ageable);
                        neighbor.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, neighbor.getLocation().add(0.5, 0.5, 0.5), 4, 0.2, 0.2, 0.2);
                     }
                  }
               }
            }
         }
      }
   }

   private void naturesWrath(Player player, Block center) {
      int radius = this.perks.farmingNaturesWrathRadius();

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            Block target = center.getRelative(dx, 0, dz);
            if (AGE_GATED.contains(target.getType()) && target.getBlockData() instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge()) {
               Material cropType = target.getType();
               int xp = this.xpValues.farming(cropType);
               this.dropExtra(player, target);
               this.replant(target.getLocation(), cropType);
               if (xp > 0) {
                  this.notifier.award(player, Skill.FARMING, xp);
               }
            }
         }
      }

      player.getWorld().spawnParticle(Particle.COMPOSTER, center.getLocation().add(0.5, 0.5, 0.5), 12, 1.0, 0.3, 1.0);
      player.playSound(player.getLocation(), Sound.ITEM_CROP_PLANT, 0.7F, 1.0F);
   }

   private void dropExtra(Player player, Block block) {
      ItemStack tool = player.getInventory().getItemInMainHand();

      for (ItemStack drop : block.getDrops(tool, player)) {
         Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack[]{drop});

         for (ItemStack leftover : overflow.values()) {
            block.getWorld().dropItemNaturally(block.getLocation(), leftover);
         }
      }
   }

   private void replant(Location loc, Material cropType) {
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         Block b = loc.getBlock();
         if (b.getType() == cropType || b.getType() == Material.AIR) {
            b.setType(cropType);
            if (b.getBlockData() instanceof Ageable ageable) {
               ageable.setAge(0);
               b.setBlockData(ageable);
            }
         }
      });
   }
}
