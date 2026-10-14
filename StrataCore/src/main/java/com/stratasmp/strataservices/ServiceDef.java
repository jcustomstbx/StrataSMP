package com.stratasmp.strataservices;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/** One thing a player can spend Stratas on. */
public record ServiceDef(String id, String name, Material icon, long price, List<String> lore, int dailyLimit,
                         String permission, List<String> commands) {

    /** Reads {@code services.*}; entries that are broken are skipped with a warning rather than crashing the module. */
    public static List<ServiceDef> parseAll(ConfigurationSection services, Consumer<String> warn) {
        List<ServiceDef> out = new ArrayList<>();
        if (services == null) return out;
        for (String id : services.getKeys(false)) {
            ConfigurationSection s = services.getConfigurationSection(id);
            if (s == null) continue;
            Material icon = Material.getMaterial(s.getString("icon", "PAPER").toUpperCase(Locale.ROOT));
            long price = s.getLong("price", -1);
            List<String> commands = s.getStringList("commands");
            if (icon == null) {
                warn.accept("Service " + id + " has an unknown icon \"" + s.getString("icon") + "\"; skipped.");
            } else if (price <= 0) {
                warn.accept("Service " + id + " needs a price above 0; skipped.");
            } else if (commands.isEmpty()) {
                warn.accept("Service " + id + " has no commands to run; skipped.");
            } else {
                out.add(new ServiceDef(id.toLowerCase(Locale.ROOT), s.getString("name", id), icon, price,
                        s.getStringList("lore"), Math.max(0, s.getInt("daily-limit", 0)),
                        s.getString("permission", ""), commands));
            }
        }
        return out;
    }
}
