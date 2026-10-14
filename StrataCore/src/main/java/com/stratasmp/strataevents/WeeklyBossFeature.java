package com.stratasmp.strataevents;

import com.stratasmp.stratabosses.StrataBosses;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

/** Spawns one boss at set weekly times, with countdown warnings. The boss pays its own Stratas and loot. */
final class WeeklyBossFeature {
    private final StrataEvents plugin;
    private final Set<String> warned = new HashSet<>();
    private final Set<String> loggedFailure = new HashSet<>();

    WeeklyBossFeature(StrataEvents plugin) {
        this.plugin = plugin;
    }

    void tick() {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("weekly-boss");
        if (cfg == null || !cfg.getBoolean("enabled", true)) return;
        ZonedDateTime now = ZonedDateTime.now(plugin.zone());
        List<Integer> warnMinutes = cfg.getIntegerList("warn-minutes");
        for (EventSchedule.Slot slot : slots(cfg.getStringList("slots"))) {
            long until = EventSchedule.minutesUntil(slot, now);
            for (int minutes : warnMinutes) {
                if (until == minutes && warned.add("boss:" + now.toLocalDate() + slot + minutes)) {
                    Bukkit.broadcast(Component.text("[Event] The weekly boss spawns in " + minutes + " minute" + (minutes == 1 ? "" : "s") + "!", NamedTextColor.GOLD));
                }
            }
            String key = EventSchedule.dueKey("boss", slot, now, cfg.getInt("catch-up-minutes", 30));
            if (key == null || plugin.alreadyFired(key)) continue;
            if (Bukkit.getOnlinePlayers().size() < cfg.getInt("min-online", 1)) continue;
            if (spawn(cfg.getString("boss", ""))) {
                plugin.markFired(key);
            } else if (loggedFailure.add(key)) {
                plugin.getLogger().warning("Weekly boss could not spawn (no eligible player or safe spot); retrying until the catch-up window ends.");
            }
        }
    }

    boolean spawn(String bossId) {
        StrataBosses bosses = plugin.core().module(StrataBosses.class);
        if (bosses == null || !bosses.isEnabled()) return false;
        String name = bosses.spawnEventBoss(bossId);
        if (name == null) return false;
        Bukkit.broadcast(Component.text("[Event] The weekly boss, " + name + ", has spawned! Gather your team.", NamedTextColor.GOLD));
        return true;
    }

    static List<EventSchedule.Slot> slots(List<String> raw) {
        List<EventSchedule.Slot> slots = new ArrayList<>();
        for (String text : raw) {
            EventSchedule.Slot slot = EventSchedule.Slot.parse(text);
            if (slot != null) slots.add(slot);
        }
        return slots;
    }
}
