package com.stratasmp.stratammo.quests;

import com.stratasmp.stratacore.StrataModule;
import com.stratasmp.strataeconomy.api.StrataApi;
import com.stratasmp.stratammo.Skill;
import com.stratasmp.stratammo.XpNotifier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Weekly quests and story chains, layered on the daily quests. Separate files per player
 * (quests-extra/&lt;uuid&gt;.yml) so the daily quest data is never touched. Rewards are marked done
 * before they are paid, so a reward can never be paid twice.
 */
public final class ExtraQuests {

    /** One line in a quest menu. */
    public record Line(String name, String detail, int progress, int amount, boolean done, String reward) {
    }

    /** One story chain as shown in the menu. */
    public record ChainView(String id, String name, String description, String icon, int step, int steps,
                            boolean done, Line current, String finale) {
    }

    private record Chain(String id, String name, String description, String icon, List<QuestDef> steps,
                         long finaleStratas, int finaleXp, Skill finaleSkill) {
    }

    private static final class Weekly {
        int progress;
        boolean done;
    }

    private static final class PlayerState {
        String week;
        final Map<String, Weekly> weekly = new LinkedHashMap<>();
        final Map<String, QuestRules.ChainState> chains = new ConcurrentHashMap<>();
        volatile boolean dirty;
    }

    private final StrataModule plugin;
    private final XpNotifier notifier;
    private final File folder;
    private final Map<String, QuestDef> weeklyPool = new LinkedHashMap<>();
    private final Map<String, Chain> chains = new LinkedHashMap<>();
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
    private final java.util.concurrent.ExecutorService io = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "StrataMMO-quests-extra-io");
        t.setDaemon(true);
        return t;
    });
    private int weeklyCount = 3;
    private DayOfWeek resetDay = DayOfWeek.MONDAY;
    private ZoneId zone = ZoneId.of("UTC");

    public ExtraQuests(StrataModule plugin, XpNotifier notifier, YamlConfiguration yaml) {
        this.plugin = plugin;
        this.notifier = notifier;
        this.folder = new File(plugin.getDataFolder(), "quests-extra");
        this.folder.mkdirs();
        configure(yaml);
    }

    private void configure(YamlConfiguration yaml) {
        try {
            zone = ZoneId.of(yaml.getString("settings.timezone", "UTC"));
        } catch (Exception e) {
            zone = ZoneId.of("UTC");
        }
        weeklyCount = Math.max(1, yaml.getInt("weekly.count", 3));
        try {
            resetDay = DayOfWeek.valueOf(yaml.getString("weekly.reset-day", "MONDAY").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Bad weekly.reset-day, using MONDAY.");
        }
        ConfigurationSection pool = yaml.getConfigurationSection("weekly.pool");
        if (pool != null) {
            for (String id : pool.getKeys(false)) {
                QuestDef def = parse(id, pool.getConfigurationSection(id));
                if (def != null) {
                    weeklyPool.put(id, def);
                }
            }
        }
        ConfigurationSection chainSection = yaml.getConfigurationSection("chains");
        if (chainSection != null) {
            for (String id : chainSection.getKeys(false)) {
                ConfigurationSection c = chainSection.getConfigurationSection(id);
                ConfigurationSection stepSection = c == null ? null : c.getConfigurationSection("steps");
                if (stepSection == null) {
                    continue;
                }
                List<QuestDef> steps = new ArrayList<>();
                for (String stepId : stepSection.getKeys(false)) {
                    QuestDef def = parse(id + "." + stepId, stepSection.getConfigurationSection(stepId));
                    if (def != null) {
                        steps.add(def);
                    }
                }
                if (steps.isEmpty()) {
                    plugin.getLogger().warning("Skipping story chain '" + id + "': no valid steps.");
                    continue;
                }
                Skill finaleSkill = skillOf(c.getString("finale.skill", ""));
                chains.put(id, new Chain(id, c.getString("name", id), c.getString("description", ""),
                        c.getString("icon", "BOOK"), List.copyOf(steps), Math.max(0L, c.getLong("finale.stratas", 0)),
                        Math.max(0, c.getInt("finale.xp", 0)), finaleSkill));
            }
        }
        plugin.getLogger().info("Quests: " + weeklyPool.size() + " weekly, " + chains.size() + " story chain(s).");
    }

    private QuestDef parse(String id, ConfigurationSection q) {
        if (q == null) {
            return null;
        }
        try {
            Set<String> targets = new HashSet<>();
            for (String t : q.getStringList("targets")) {
                targets.add(t.toUpperCase(Locale.ROOT));
            }
            return new QuestDef(id, q.getString("name", id),
                    ObjectiveType.valueOf(q.getString("type", "").toUpperCase(Locale.ROOT)), targets,
                    Math.max(1, q.getInt("amount", 1)), Skill.valueOf(q.getString("skill", "").toUpperCase(Locale.ROOT)),
                    Math.max(0, q.getInt("xp", 0)), Math.max(0L, q.getLong("stratas", 0)));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Skipping quest '" + id + "': bad type or skill.");
            return null;
        }
    }

    private static Skill skillOf(String name) {
        try {
            return Skill.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---- loading and saving ----

    public void load(Player player) {
        UUID id = player.getUniqueId();
        io.execute(() -> {
            PlayerState state = new PlayerState();
            File file = new File(folder, id + ".yml");
            if (file.exists()) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                state.week = yaml.getString("week");
                ConfigurationSection weekly = yaml.getConfigurationSection("weekly");
                if (weekly != null) {
                    for (String key : weekly.getKeys(false)) {
                        Weekly w = new Weekly();
                        w.progress = weekly.getInt(key + ".progress");
                        w.done = weekly.getBoolean(key + ".done");
                        state.weekly.put(key, w);
                    }
                }
                ConfigurationSection saved = yaml.getConfigurationSection("chains");
                if (saved != null) {
                    for (String key : saved.getKeys(false)) {
                        state.chains.put(key, new QuestRules.ChainState(saved.getInt(key + ".step"),
                                saved.getInt(key + ".progress"), saved.getBoolean(key + ".done")));
                    }
                }
            }
            if (plugin.getServer().getPlayer(id) != null) {
                states.put(id, state);
            }
        });
    }

    public void unload(Player player) {
        PlayerState state = states.remove(player.getUniqueId());
        if (state != null) {
            write(player.getUniqueId(), snapshot(state), true);
        }
    }

    public void saveAll(boolean async) {
        for (Map.Entry<UUID, PlayerState> e : states.entrySet()) {
            if (e.getValue().dirty) {
                e.getValue().dirty = false;
                write(e.getKey(), snapshot(e.getValue()), async);
            }
        }
    }

    public void shutdown() {
        saveAll(false);
        io.shutdown();
    }

    private YamlConfiguration snapshot(PlayerState state) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("week", state.week);
        for (Map.Entry<String, Weekly> e : state.weekly.entrySet()) {
            yaml.set("weekly." + e.getKey() + ".progress", e.getValue().progress);
            yaml.set("weekly." + e.getKey() + ".done", e.getValue().done);
        }
        for (Map.Entry<String, QuestRules.ChainState> e : state.chains.entrySet()) {
            yaml.set("chains." + e.getKey() + ".step", e.getValue().step());
            yaml.set("chains." + e.getKey() + ".progress", e.getValue().progress());
            yaml.set("chains." + e.getKey() + ".done", e.getValue().done());
        }
        return yaml;
    }

    private void write(UUID id, YamlConfiguration yaml, boolean async) {
        Runnable job = () -> {
            File target = new File(folder, id + ".yml");
            File temp = new File(folder, id + ".yml.tmp");
            try {
                yaml.save(temp);
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save story quests for " + id + ": " + e.getMessage());
            }
        };
        if (async && !io.isShutdown()) {
            io.execute(job);
        } else {
            job.run();
        }
    }

    // ---- state ----

    private PlayerState current(UUID id) {
        PlayerState state = states.get(id);
        if (state == null) {
            return null;
        }
        String week = QuestRules.weekKey(LocalDate.now(zone), resetDay);
        if (!week.equals(state.week) || (state.weekly.isEmpty() && !weeklyPool.isEmpty())) {
            state.week = week;
            state.weekly.clear();
            for (QuestDef def : QuestRules.pickWeekly(new ArrayList<>(weeklyPool.values()), weeklyCount, new Random())) {
                state.weekly.put(def.id(), new Weekly());
            }
            state.dirty = true;
        }
        return state;
    }

    // ---- progress ----

    public void progress(Player player, ObjectiveType type, String key, int amount) {
        PlayerState state = current(player.getUniqueId());
        if (state == null || amount <= 0) {
            return;
        }
        for (Map.Entry<String, Weekly> e : state.weekly.entrySet()) {
            QuestDef def = weeklyPool.get(e.getKey());
            Weekly w = e.getValue();
            if (def == null || w.done || !def.matches(type, key)) {
                continue;
            }
            w.progress = Math.min(def.amount(), w.progress + amount);
            state.dirty = true;
            if (w.progress >= def.amount()) {
                w.done = true; // marked before paying so it can never pay twice
                pay(player, "Weekly quest complete: " + def.name(), def.skill(), def.xp(), def.stratas());
            }
        }
        for (Chain chain : chains.values()) {
            QuestRules.ChainState cs = state.chains.computeIfAbsent(chain.id(), k -> new QuestRules.ChainState());
            if (cs.done()) {
                continue;
            }
            int stepIndex = Math.min(cs.step(), chain.steps().size() - 1);
            QuestDef def = chain.steps().get(stepIndex);
            if (!def.matches(type, key)) {
                continue;
            }
            QuestRules.Result result = cs.add(amount, def.amount(), chain.steps().size());
            state.dirty = true;
            if (result == QuestRules.Result.STEP_DONE || result == QuestRules.Result.CHAIN_DONE) {
                pay(player, chain.name() + " - step " + (stepIndex + 1) + "/" + chain.steps().size() + " complete: "
                        + def.name(), def.skill(), def.xp(), def.stratas());
            }
            if (result == QuestRules.Result.CHAIN_DONE) {
                pay(player, "Story complete: " + chain.name(), chain.finaleSkill(), chain.finaleXp(), chain.finaleStratas());
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            }
        }
    }

    private void pay(Player player, String title, Skill skill, int xp, long stratas) {
        StringBuilder reward = new StringBuilder();
        if (stratas > 0) {
            StrataApi api = StrataApi.get();
            if (api != null) {
                api.deposit(player.getUniqueId(), stratas);
                reward.append("  +").append(api.format(stratas));
            }
        }
        if (xp > 0 && skill != null && notifier.awardExact(player, skill, xp)) {
            reward.append("  +").append(xp).append(' ').append(skill.displayName()).append(" XP");
        }
        player.sendMessage(Component.text(title, NamedTextColor.GREEN)
                .append(Component.text(reward.toString(), NamedTextColor.GOLD)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
    }

    // ---- views for the menu and placeholders ----

    private String rewardText(QuestDef def) {
        StringBuilder sb = new StringBuilder();
        if (def.stratas() > 0) {
            StrataApi api = StrataApi.get();
            sb.append(api != null ? api.format(def.stratas()) : def.stratas() + " Stratas");
        }
        if (def.xp() > 0) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(def.xp()).append(' ').append(def.skill().displayName()).append(" XP");
        }
        return sb.toString();
    }

    public List<Line> weeklyView(UUID id) {
        PlayerState state = current(id);
        List<Line> lines = new ArrayList<>();
        if (state == null) {
            return lines;
        }
        for (Map.Entry<String, Weekly> e : state.weekly.entrySet()) {
            QuestDef def = weeklyPool.get(e.getKey());
            if (def != null) {
                lines.add(new Line(def.name(), def.describe(), e.getValue().progress, def.amount(), e.getValue().done,
                        rewardText(def)));
            }
        }
        return lines;
    }

    public List<ChainView> chainViews(UUID id) {
        PlayerState state = states.get(id);
        List<ChainView> views = new ArrayList<>();
        for (Chain chain : chains.values()) {
            QuestRules.ChainState cs = state == null ? null : state.chains.get(chain.id());
            int step = cs == null ? 0 : Math.min(cs.step(), chain.steps().size() - 1);
            boolean done = cs != null && cs.done();
            QuestDef def = chain.steps().get(step);
            int progress = cs == null ? 0 : cs.progress();
            StringBuilder finale = new StringBuilder();
            if (chain.finaleStratas() > 0) {
                finale.append(chain.finaleStratas()).append(" Stratas");
            }
            if (chain.finaleXp() > 0 && chain.finaleSkill() != null) {
                finale.append(finale.length() > 0 ? " + " : "").append(chain.finaleXp()).append(' ')
                        .append(chain.finaleSkill().displayName()).append(" XP");
            }
            views.add(new ChainView(chain.id(), chain.name(), chain.description(), chain.icon(), step,
                    chain.steps().size(), done, new Line(def.name(), def.describe(), done ? def.amount() : progress,
                    def.amount(), done, rewardText(def)), finale.toString()));
        }
        return views;
    }

    public Duration untilWeeklyReset() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = now.toLocalDate().with(TemporalAdjusters.next(resetDay)).atStartOfDay(zone);
        return Duration.between(now, next);
    }

    /** {done, total} of this week's quests, or null while they load. Safe off the main thread. */
    public int[] weeklySummary(UUID id) {
        PlayerState state = states.get(id);
        if (state == null) {
            return null;
        }
        try {
            int done = 0;
            int total = 0;
            for (Weekly w : state.weekly.values()) {
                total++;
                if (w.done) {
                    done++;
                }
            }
            return new int[]{done, total};
        } catch (java.util.ConcurrentModificationException e) {
            return null;
        }
    }

    /** A short label for the chain the player is partway through, e.g. "Miner's Path 2/5". */
    public String chainSummary(UUID id) {
        PlayerState state = states.get(id);
        if (state == null) {
            return "None";
        }
        for (Chain chain : chains.values()) {
            QuestRules.ChainState cs = state.chains.get(chain.id());
            if (cs != null && !cs.done() && (cs.step() > 0 || cs.progress() > 0)) {
                return chain.name() + " " + (cs.step() + 1) + "/" + chain.steps().size();
            }
        }
        return "None";
    }
}
