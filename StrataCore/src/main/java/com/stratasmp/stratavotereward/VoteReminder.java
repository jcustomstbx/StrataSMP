package com.stratasmp.stratavotereward;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import com.stratasmp.stratacore.StrataModule;

public class VoteReminder implements Runnable {
   private final StrataModule plugin;

   public VoteReminder(StrataModule plugin) {
      this.plugin = plugin;
   }

   public static void start(StrataModule plugin) {
      if (plugin.getConfig().getBoolean("reminder.enabled", true)) {
         long ticks = plugin.getConfig().getLong("reminder.interval-minutes", 30L) * 60L * 20L;
         Bukkit.getScheduler().runTaskTimer(plugin, new VoteReminder(plugin), ticks, ticks);
      }
   }

   @Override
   public void run() {
      if (!Bukkit.getOnlinePlayers().isEmpty()) {
         Bukkit.broadcast(buildVoteMessage(this.plugin));

         for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.4F);
         }
      }
   }

   public static Component buildVoteMessage(StrataModule plugin) {
      String message = plugin.getConfig().getString("reminder.message", "&e★ &fVote for StrataSMP and get &6350 Stratas&f! &e★");
      String link = plugin.getConfig().getString("reminder.link", "https://stratasmp.com/vote");
      return LegacyComponentSerializer.legacyAmpersand()
         .deserialize(message)
         .append(
            ((TextComponent)Component.text(" » Click to vote!", NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(link)))
               .hoverEvent(HoverEvent.showText(Component.text("Open " + link, NamedTextColor.GRAY)))
         );
   }
}
