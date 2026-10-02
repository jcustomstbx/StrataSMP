package com.stratasmp.strataweapons;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import com.stratasmp.stratacore.StrataModule;

public class KillMessageListener implements Listener {
   private static final Pattern TOKEN = Pattern.compile("\\{killer}|\\{victim}|\\{weapon}");
   private static final String USE_PERMISSION = "strataweapons.killmessage.use";
   private final StrataModule plugin;
   private final WeaponCatalog catalog;
   private final KillMessageManager messages;

   public KillMessageListener(StrataModule plugin, WeaponCatalog catalog, KillMessageManager messages) {
      this.plugin = plugin;
      this.catalog = catalog;
      this.messages = messages;
   }

   @EventHandler
   public void onDeath(PlayerDeathEvent event) {
      if (this.plugin.getConfig().getBoolean("kill-messages.enabled", true)) {
         Player killer = event.getEntity().getKiller();
         if (killer != null && !killer.getUniqueId().equals(event.getEntity().getUniqueId())) {
            ItemStack weapon = killer.getInventory().getItemInMainHand();
            String weaponKey = this.catalog.weaponKeyOf(weapon);
            if (weaponKey != null) {
               event.deathMessage(null);
               boolean useCustom = killer.hasPermission("strataweapons.killmessage.use") && this.messages.hasCustom(killer.getUniqueId());
               String template = useCustom ? this.messages.templateFor(killer.getUniqueId()) : this.messages.defaultTemplate();
               Component built = this.build(template, killer.getName(), event.getEntity().getName(), this.catalog.displayNameOf(weaponKey));
               Bukkit.broadcast(built);
            }
         }
      }
   }

   private Component build(String template, String killerName, String victimName, String weaponName) {
      Component result = Component.empty();
      Matcher matcher = TOKEN.matcher(template);

      int last;
      for (last = 0; matcher.find(); last = matcher.end()) {
         if (matcher.start() > last) {
            result = result.append(Component.text(template.substring(last, matcher.start()), NamedTextColor.GRAY));
         }

         String var8 = matcher.group();

         result = result.append(switch (var8) {
            case "{killer}" -> (TextComponent)Component.text(killerName, NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true);
            case "{victim}" -> (TextComponent)Component.text(victimName, NamedTextColor.RED).decoration(TextDecoration.BOLD, true);
            default -> (TextComponent)Component.text(weaponName, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, true);
         });
      }

      if (last < template.length()) {
         result = result.append(Component.text(template.substring(last), NamedTextColor.GRAY));
      }

      return result;
   }
}
