package com.stratasmp.strataeconomy.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import com.stratasmp.stratacore.StrataModule;

import java.io.File;
import java.util.Map;

public final class Msg {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final FileConfiguration cfg;
    private final String prefix;

    public Msg(StrataModule plugin) {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.cfg = YamlConfiguration.loadConfiguration(f);
        this.prefix = cfg.getString("prefix", "");
    }

    public Component get(String key, Map<String, String> placeholders) {
        String raw = cfg.getString(key, key);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                raw = raw.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return LEGACY.deserialize(prefix + raw);
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
