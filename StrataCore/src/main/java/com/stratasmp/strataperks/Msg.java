package com.stratasmp.strataperks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import com.stratasmp.stratacore.StrataModule;

import java.util.Map;

public final class Msg {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final StrataModule plugin;

    public Msg(StrataModule plugin) {
        this.plugin = plugin;
    }

    private String prefix() {
        return plugin.getConfig().getString("messages.prefix", "");
    }

    public Component get(String key, Map<String, String> placeholders) {
        String raw = plugin.getConfig().getString("messages." + key, key);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                raw = raw.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return LEGACY.deserialize(prefix() + raw);
    }

    public Component plain(String key) {
        return get(key, null);
    }

    public void send(CommandSender to, String key, Map<String, String> placeholders) {
        to.sendMessage(get(key, placeholders));
    }

    public void send(CommandSender to, String key) {
        to.sendMessage(get(key, null));
    }

    public static Component color(String legacy) {
        return LEGACY.deserialize(legacy);
    }
}
