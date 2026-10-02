package com.stratasmp.stratammo.quests;

import com.stratasmp.stratacore.StrataModule;
import com.stratasmp.stratammo.LevelCurve;
import com.stratasmp.stratammo.Skill;
import com.stratasmp.stratammo.XpNotifier;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Daily per-player quests that pay skill xp. State lives in plugins/StrataMMO/quests/&lt;uuid&gt;.yml. */
public final class QuestManager {
   private static final class Entry {
      int progress;
      boolean done;
   }

   private static final class State {
      String date;
      final Map<String, Entry> entries = new LinkedHashMap<>();
      boolean dirty;
   }

   private final StrataModule plugin;
   private final XpNotifier notifier;
   private final LevelCurve curve;
   private final File folder;
   private final Map<String, QuestDef> pool = new LinkedHashMap<>();
   private final Map<UUID, State> states = new ConcurrentHashMap<>();
   private int dailyCount = 3;
   private ZoneId zone = ZoneId.of("UTC");

   public QuestManager(StrataModule plugin, XpNotifier notifier, LevelCurve curve) {
      this.plugin = plugin;
      this.notifier = notifier;
      this.curve = curve;
      this.folder = new File(plugin.getDataFolder(), "quests");
      this.folder.mkdirs();
      this.loadPool();
      plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveDirty, 6000L, 6000L);
   }

   private void loadPool() {
      File file = new File(plugin.getDataFolder(), "quests.yml");
      if (!file.exists()) plugin.saveResource("quests.yml", false);
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
      this.dailyCount = Math.max(1, yaml.getInt("settings.daily-count", 3));
      try {
         this.zone = ZoneId.of(yaml.getString("settings.timezone", "UTC"));
      } catch (Exception e) {
         plugin.getLogger().warning("Bad quest timezone, using UTC.");
      }
      ConfigurationSection section = yaml.getConfigurationSection("pool");
      if (section == null) return;
      for (String id : section.getKeys(false)) {
         ConfigurationSection q = section.getConfigurationSection(id);
         if (q == null) continue;
         try {
            Set<String> targets = new HashSet<>();
            for (String t : q.getStringList("targets")) targets.add(t.toUpperCase(Locale.ROOT));
            this.pool.put(id, new QuestDef(id, q.getString("name", id),
                  ObjectiveType.valueOf(q.getString("type", "").toUpperCase(Locale.ROOT)), targets,
                  Math.max(1, q.getInt("amount", 1)), Skill.valueOf(q.getString("skill", "").toUpperCase(Locale.ROOT)),
                  Math.max(0, q.getInt("xp", 0))));
         } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Skipping quest '" + id + "': bad type or skill.");
         }
      }
   }

   private String today() {
      return LocalDate.now(this.zone).toString();
   }

   public void load(Player player) {
      UUID id = player.getUniqueId();
      plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
         State state = new State();
         File file = new File(this.folder, id + ".yml");
         if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            state.date = yaml.getString("date");
            ConfigurationSection quests = yaml.getConfigurationSection("quests");
            if (quests != null) {
               for (String key : quests.getKeys(false)) {
                  Entry entry = new Entry();
                  entry.progress = quests.getInt(key + ".progress");
                  entry.done = quests.getBoolean(key + ".done");
                  state.entries.put(key, entry);
               }
            }
         }
         this.states.put(id, state);
      });
   }

   public void unload(Player player) {
      State state = this.states.remove(player.getUniqueId());
      if (state != null) this.write(player.getUniqueId(), snapshot(state), true);
   }

   private State current(UUID id) {
      State state = this.states.get(id);
      if (state == null) return null;
      String today = this.today();
      if (!today.equals(state.date) || state.entries.isEmpty()) {
         state.date = today;
         state.entries.clear();
         List<QuestDef> shuffled = new ArrayList<>(this.pool.values());
         Collections.shuffle(shuffled);
         Set<Skill> usedSkills = new HashSet<>();
         List<QuestDef> picked = new ArrayList<>();
         for (QuestDef def : shuffled) {
            if (picked.size() < this.dailyCount && usedSkills.add(def.skill())) picked.add(def);
         }
         for (QuestDef def : shuffled) {
            if (picked.size() < this.dailyCount && !picked.contains(def)) picked.add(def);
         }
         for (QuestDef def : picked) state.entries.put(def.id(), new Entry());
         state.dirty = true;
      }
      return state;
   }

   public void progress(Player player, ObjectiveType type, String key, int amount) {
      if (this.pool.isEmpty()) return;
      State state = this.current(player.getUniqueId());
      if (state == null) return;
      for (Map.Entry<String, Entry> e : state.entries.entrySet()) {
         QuestDef def = this.pool.get(e.getKey());
         Entry entry = e.getValue();
         if (def == null || entry.done || !def.matches(type, key)) continue;
         entry.progress = Math.min(def.amount(), entry.progress + amount);
         state.dirty = true;
         if (entry.progress >= def.amount()) {
            entry.done = true;
            player.sendMessage(Component.text("Quest complete: ", NamedTextColor.GREEN)
                  .append(Component.text(def.name(), NamedTextColor.WHITE))
                  .append(Component.text("  +" + def.xp() + " " + def.skill().displayName() + " XP", NamedTextColor.GOLD)));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            this.notifier.awardExact(player, def.skill(), def.xp());
         }
      }
   }

   public void show(Player player) {
      State state = this.current(player.getUniqueId());
      if (state == null) {
         player.sendMessage(Component.text("Your quests are still loading, try again in a moment.", NamedTextColor.RED));
         return;
      }
      ZonedDateTime now = ZonedDateTime.now(this.zone);
      Duration left = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(this.zone));
      player.sendMessage(Component.text("Daily Quests", NamedTextColor.GOLD)
            .append(Component.text("  (resets in " + left.toHours() + "h " + left.toMinutesPart() + "m)", NamedTextColor.GRAY)));
      for (Map.Entry<String, Entry> e : state.entries.entrySet()) {
         QuestDef def = this.pool.get(e.getKey());
         if (def == null) continue;
         Entry entry = e.getValue();
         player.sendMessage(Component.text(entry.done ? " ✔ " : " ▸ ", entry.done ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
               .append(Component.text(def.describe() + " ", NamedTextColor.WHITE))
               .append(Component.text(entry.progress + "/" + def.amount(), NamedTextColor.AQUA))
               .append(Component.text("  +" + def.xp() + " " + def.skill().displayName() + " XP", NamedTextColor.GOLD)));
      }
   }

   private YamlConfiguration snapshot(State state) {
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.set("date", state.date);
      for (Map.Entry<String, Entry> e : state.entries.entrySet()) {
         yaml.set("quests." + e.getKey() + ".progress", e.getValue().progress);
         yaml.set("quests." + e.getKey() + ".done", e.getValue().done);
      }
      return yaml;
   }

   private void write(UUID id, YamlConfiguration yaml, boolean async) {
      Runnable job = () -> {
         try {
            yaml.save(new File(this.folder, id + ".yml"));
         } catch (IOException e) {
            plugin.getLogger().warning("Could not save quests for " + id + ": " + e.getMessage());
         }
      };
      if (async && plugin.isEnabled()) plugin.getServer().getScheduler().runTaskAsynchronously(plugin, job);
      else job.run();
   }

   private void saveDirty() {
      for (Map.Entry<UUID, State> e : this.states.entrySet()) {
         if (e.getValue().dirty) {
            e.getValue().dirty = false;
            this.write(e.getKey(), this.snapshot(e.getValue()), true);
         }
      }
   }

   public void saveAllNow() {
      for (Map.Entry<UUID, State> e : this.states.entrySet()) {
         this.write(e.getKey(), this.snapshot(e.getValue()), false);
      }
   }
}
