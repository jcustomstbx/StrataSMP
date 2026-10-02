package com.stratasmp.stratammo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class UltimatePerks {
   private final XpNotifier notifier;
   private final PerkCooldowns cooldowns;
   private final PerkSettings settings;
   private final ActiveUltimates activeUltimates;

   public UltimatePerks(XpNotifier notifier, PerkCooldowns cooldowns, PerkSettings settings, ActiveUltimates activeUltimates) {
      this.notifier = notifier;
      this.cooldowns = cooldowns;
      this.settings = settings;
      this.activeUltimates = activeUltimates;
   }

   public boolean activate(Player player, Skill skill, String ultimateName) {
      if (!this.settings.enabled()) {
         player.sendMessage(Component.text("Perks are currently disabled on this server.", NamedTextColor.RED));
         return false;
      } else if (this.notifier.levelOf(player, skill) < 100) {
         player.sendMessage(Component.text("You need level 100 " + skill.displayName() + " to use this.", NamedTextColor.RED));
         return false;
      } else {
         String key = skill.name() + ":ultimate";
         if (!this.cooldowns.tryTrigger(player.getUniqueId(), key, this.settings.ultimateCooldownSeconds())) {
            long left = this.cooldowns.secondsLeft(player.getUniqueId(), key);
            player.sendMessage(Component.text(ultimateName + " is on cooldown for " + this.formatTime(left) + ".", NamedTextColor.RED));
            return false;
         } else {
            this.activeUltimates.activate(player.getUniqueId(), skill, this.settings.ultimateDurationSeconds());
            player.sendActionBar(Component.text(ultimateName + " activated! (" + this.settings.ultimateDurationSeconds() + "s)", NamedTextColor.LIGHT_PURPLE));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6F, 1.6F);
            return true;
         }
      }
   }

   private String formatTime(long seconds) {
      long minutes = seconds / 60L;
      long secs = seconds % 60L;
      return minutes <= 0L ? secs + "s" : minutes + "m " + secs + "s";
   }
}
