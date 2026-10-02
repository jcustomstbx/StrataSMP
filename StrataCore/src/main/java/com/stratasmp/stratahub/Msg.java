package com.stratasmp.stratahub;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import com.stratasmp.stratacore.StrataModule;

public final class Msg {

    private final StrataModule plugin;

    public Msg(StrataModule plugin) {
        this.plugin = plugin;
    }

    public void send(CommandSender to, String key) {
        send(to, key, null, null);
    }

    public void send(CommandSender to, String key, String placeholder, String value) {
        String raw = plugin.getConfig().getString("messages." + key, key);
        if (placeholder != null) {
            raw = raw.replace(placeholder, value);
        }
        to.sendMessage(ChatColor.translateAlternateColorCodes('&', raw));
    }
}
