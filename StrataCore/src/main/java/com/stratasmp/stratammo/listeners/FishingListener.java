package com.stratasmp.stratammo.listeners;

import com.stratasmp.stratammo.ActiveUltimates;
import com.stratasmp.stratammo.PerkCooldowns;
import com.stratasmp.stratammo.PerkSettings;
import com.stratasmp.stratammo.Skill;
import com.stratasmp.stratammo.XpNotifier;
import com.stratasmp.stratammo.XpValues;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerFishEvent.State;
import org.bukkit.inventory.ItemStack;

public class FishingListener implements Listener {
   private static final Set<Material> FISH = Set.of(Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH);
   private static final Set<Material> JUNK = Set.of(
      Material.LILY_PAD,
      Material.BOWL,
      Material.LEATHER,
      Material.LEATHER_BOOTS,
      Material.ROTTEN_FLESH,
      Material.STICK,
      Material.STRING,
      Material.BONE,
      Material.INK_SAC,
      Material.TRIPWIRE_HOOK
   );
   private static final List<Material> TREASURE_POOL = List.of(Material.NAME_TAG, Material.SADDLE, Material.NAUTILUS_SHELL, Material.BOW);
   private final XpValues xpValues;
   private final XpNotifier notifier;
   private final PerkSettings perks;
   private final PerkCooldowns cooldowns;
   private final ActiveUltimates activeUltimates;

   public FishingListener(XpValues xpValues, XpNotifier notifier, PerkSettings perks, PerkCooldowns cooldowns, ActiveUltimates activeUltimates) {
      this.xpValues = xpValues;
      this.notifier = notifier;
      this.perks = perks;
      this.cooldowns = cooldowns;
      this.activeUltimates = activeUltimates;
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onFish(PlayerFishEvent event) {
      Player player = event.getPlayer();
      if (!this.notifier.accepts(player)) return;
      if (event.getState() == State.FISHING) {
         if (this.perks.enabled()) {
            FishHook hook = event.getHook();
            int level = this.notifier.levelOf(player, Skill.FISHING);
            if (this.activeUltimates.isActive(player.getUniqueId(), Skill.FISHING)) {
               hook.setMinWaitTime(1);
               hook.setMaxWaitTime(20);
            } else if (level >= 25) {
               double reduction = this.perks.fishingReflexesWaitReductionPercent() / 100.0;
               hook.setMinWaitTime((int)(hook.getMinWaitTime() * (1.0 - reduction)));
               hook.setMaxWaitTime((int)(hook.getMaxWaitTime() * (1.0 - reduction)));
            }
         }
      } else if (event.getState() == State.CAUGHT_FISH) {
         if (event.getCaught() instanceof Item caughtItem) {
            Material var10 = caughtItem.getItemStack().getType();
            int xp = FISH.contains(var10) ? this.xpValues.fishingCatch() : this.xpValues.fishingTreasure();
            this.notifier.award(player, Skill.FISHING, xp);
            if (this.perks.enabled()) {
               int level = this.notifier.levelOf(player, Skill.FISHING);
               boolean ultimateActive = this.activeUltimates.isActive(player.getUniqueId(), Skill.FISHING);
               if (ultimateActive) {
                  Material treasure = TREASURE_POOL.get(ThreadLocalRandom.current().nextInt(TREASURE_POOL.size()));
                  caughtItem.setItemStack(new ItemStack(treasure, 1));
                  player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.3F);
               } else {
                  if (level >= 50 && JUNK.contains(var10)) {
                     this.treasureHunter(player, caughtItem);
                  }

                  Material finalType = caughtItem.getItemStack().getType();
                  if (level >= 75) {
                     this.catchAndRelease(player, caughtItem, finalType);
                  }
               }
            }
         }
      }
   }

   private void treasureHunter(Player player, Item caughtItem) {
      if (ThreadLocalRandom.current().nextInt(100) < this.perks.fishingTreasureHunterBonusPercent()) {
         if (this.cooldowns.tryTrigger(player.getUniqueId(), "FISHING:treasurehunter", this.perks.utilityCooldownSeconds())) {
            Material treasure = TREASURE_POOL.get(ThreadLocalRandom.current().nextInt(TREASURE_POOL.size()));
            caughtItem.setItemStack(new ItemStack(treasure, 1));
         }
      }
   }

   private void catchAndRelease(Player player, Item caughtItem, Material type) {
      if (ThreadLocalRandom.current().nextInt(100) < this.perks.fishingCatchAndReleaseChance()) {
         if (this.cooldowns.tryTrigger(player.getUniqueId(), "FISHING:catchrelease", this.perks.conservationCooldownSeconds())) {
            if (FISH.contains(type)) {
               caughtItem.getWorld().dropItemNaturally(caughtItem.getLocation(), caughtItem.getItemStack().clone());
            } else if (JUNK.contains(type)) {
               caughtItem.remove();
               this.notifier.award(player, Skill.FISHING, this.xpValues.fishingCatch());
            }
         }
      }
   }
}
