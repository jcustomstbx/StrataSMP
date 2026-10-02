package com.stratasmp.strataweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

/** Menus can't open URLs themselves, so the store button closes the menu and sends a clickable chat link. */
final class StoreLink {

    private StoreLink() {
    }

    static void send(StrataWeapons plugin, Player player) {
        String url = plugin.getConfig().getString("store-url", "https://stratasmp.com/store");
        player.closeInventory();
        player.sendMessage(Component.text("» Click here to open the store", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text(url, NamedTextColor.GRAY))));
    }
}
