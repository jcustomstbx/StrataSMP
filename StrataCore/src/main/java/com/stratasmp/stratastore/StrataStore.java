package com.stratasmp.stratastore;

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class StrataStore extends StrataModule implements CommandExecutor {
   public StrataStore(StrataCore core) {
      super(core, "StrataStore");
   }

   private static final String STORE_URL = "https://stratasmp.com/store";

   public void onEnable() {
      this.getCommand("store").setExecutor(this);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      Component link = ((TextComponent)Component.text("stratasmp.com/store", NamedTextColor.GOLD, new TextDecoration[]{TextDecoration.UNDERLINED})
            .clickEvent(ClickEvent.openUrl("https://stratasmp.com/store")))
         .hoverEvent(HoverEvent.showText(Component.text("Click to open in your browser")));
      sender.sendMessage(Component.text("Grab ranks and perks at ", NamedTextColor.YELLOW).append(link));
      return true;
   }
}
