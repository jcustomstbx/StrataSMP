package com.stratasmp.stratammo;

import com.stratasmp.strataeconomy.api.StrataApi;
import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent.Builder;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import java.util.Locale;
import java.util.Set;

public class XpNotifier {
   private final DataManager dataManager;
   private final LevelCurve levelCurve;
   private final int milestoneInterval;
   private final boolean stratasRewardEnabled;
   private final int stratasRewardInterval;
   private final long stratasRewardAmount;
   private final String profile;
   private final Set<String> worlds;
   private double xpMultiplier = 1.0;
   private com.stratasmp.stratammo.quests.QuestManager quests;

   public XpNotifier(DataManager dataManager, LevelCurve levelCurve, int milestoneInterval,
                      boolean stratasRewardEnabled, int stratasRewardInterval, long stratasRewardAmount,
                      String profile, Set<String> worlds) {
      this.dataManager = dataManager;
      this.levelCurve = levelCurve;
      this.milestoneInterval = milestoneInterval;
      this.stratasRewardEnabled = stratasRewardEnabled;
      this.stratasRewardInterval = stratasRewardInterval;
      this.stratasRewardAmount = stratasRewardAmount;
      this.profile = profile;
      this.worlds = worlds.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
   }

   public int levelOf(Player player, Skill skill) {
      if (!this.accepts(player)) return 0;
      PlayerData data = this.dataManager.get(player.getUniqueId());
      return data == null ? 0 : this.levelCurve.levelFromTotalXp(data.getXp(skill))[0];
   }

   public void setXpMultiplier(double xpMultiplier) {
      this.xpMultiplier = xpMultiplier;
   }

   public void setQuests(com.stratasmp.stratammo.quests.QuestManager quests) {
      this.quests = quests;
   }

   public void quest(Player player, com.stratasmp.stratammo.quests.ObjectiveType type, String key, int amount) {
      if (this.quests != null) this.quests.progress(player, type, key, amount);
   }

   public void award(Player player, Skill skill, int amount) {
      this.awardExact(player, skill, amount > 0 ? Math.max(1, (int) Math.round(amount * this.xpMultiplier)) : amount);
   }

   /** Awards xp without the global multiplier (quest rewards pay out exactly what they advertise). */
   public void awardExact(Player player, Skill skill, int amount) {
      if (amount > 0 && this.accepts(player)) {
         PlayerData data = this.dataManager.get(player.getUniqueId());
         if (data != null) {
            int before = this.levelCurve.levelFromTotalXp(data.getXp(skill))[0];
            if (!this.levelCurve.isCapped() || before < this.levelCurve.maxLevel()) {
               data.addXp(skill, amount);
               int after = this.levelCurve.levelFromTotalXp(data.getXp(skill))[0];
               player.sendActionBar(Component.text("+" + amount + " " + skill.displayName() + " XP", NamedTextColor.YELLOW));
               if (after > before) {
                  Title title = Title.title(
                     Component.text(skill.displayName() + " Level Up!", NamedTextColor.GOLD),
                     Component.text("You are now level " + after, NamedTextColor.WHITE),
                     Times.times(Duration.ofMillis(500L), Duration.ofMillis(2500L), Duration.ofMillis(500L))
                  );
                  player.showTitle(title);
                  player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
                  this.announceIfMilestone(player, skill, before, after);
                  this.awardStratasIfMilestone(player, skill, before, after);
               }
            }
         }
      }
   }

   private void awardStratasIfMilestone(Player player, Skill skill, int before, int after) {
      if (!"smp".equals(this.profile) || !this.stratasRewardEnabled || this.stratasRewardInterval <= 0 || this.stratasRewardAmount <= 0) {
         return;
      }
      StrataApi stratas = StrataApi.get();
      if (stratas == null) {
         return;
      }
      int thresholdsCrossed = 0;
      for (int level = before + 1; level <= after; level++) {
         if (level % this.stratasRewardInterval == 0) {
            thresholdsCrossed++;
         }
      }
      if (thresholdsCrossed > 0) {
         long reward = thresholdsCrossed * this.stratasRewardAmount;
         stratas.deposit(player.getUniqueId(), reward);
         player.sendMessage(
            Component.text("+" + reward + " Stratas", NamedTextColor.GOLD)
               .append(Component.text(" for reaching level " + after + " " + skill.displayName() + "!", NamedTextColor.YELLOW))
         );
      }
   }

   private void announceIfMilestone(Player player, Skill skill, int before, int after) {
      if (this.milestoneInterval > 0) {
         for (int level = before + 1; level <= after; level++) {
            if (level % this.milestoneInterval == 0) {
               Component milestone =
                     ((Builder)((Builder)((Builder)Component.text().append(Component.text("★ ", NamedTextColor.GOLD)))
                              .append(Component.text(player.getName(), NamedTextColor.WHITE)))
                           .append(Component.text(" reached level " + level + " " + skill.displayName() + "!", NamedTextColor.YELLOW)))
                        .build();
               if ("factions".equals(this.profile)) player.getWorld().getPlayers().forEach(member -> member.sendMessage(milestone));
               else player.getServer().broadcast(milestone);
               break;
            }
         }
      }
   }

   public boolean accepts(Player player) {
      return player.getWorld() != null && this.worlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT));
   }

   public boolean accepts(org.bukkit.Location location) {
      return location != null && location.getWorld() != null
            && this.worlds.contains(location.getWorld().getName().toLowerCase(Locale.ROOT));
   }
}
