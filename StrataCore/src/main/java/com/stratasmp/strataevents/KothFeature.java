package com.stratasmp.strataevents;

import com.stratasmp.strataeconomy.StrataEconomy;
import com.stratasmp.stratakeystones.KeystoneRewards;
import com.stratasmp.stratateams.StrataTeams;
import com.stratasmp.stratateams.Team;
import java.io.File;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/** King of the Hill: hold the zone alone (as a player or a team) for long enough to win. */
final class KothFeature implements Listener {
    private final StrataEvents plugin;
    private final File zoneFile;
    private final Map<String, KothZone> zones = new LinkedHashMap<>();
    private final Set<String> warned = new HashSet<>();
    private final Set<String> loggedNoZones = new HashSet<>();
    private Active active;

    private static final class Active {
        final KothZone zone;
        final HillState hill;
        final BossBar bar;
        final long startedAt = System.currentTimeMillis();
        HillState.Status lastStatus = HillState.Status.EMPTY;
        String lastCapturer;

        Active(KothZone zone, HillState hill, BossBar bar) {
            this.zone = zone;
            this.hill = hill;
            this.bar = bar;
        }
    }

    KothFeature(StrataEvents plugin) {
        this.plugin = plugin;
        this.zoneFile = new File(plugin.getDataFolder(), "koth-zones.yml");
        loadZones();
    }

    private ConfigurationSection cfg() {
        return plugin.getConfig().getConfigurationSection("koth");
    }

    /* ---- zones ---- */

    void loadZones() {
        zones.clear();
        YamlConfiguration yaml = com.stratasmp.stratacore.AtomicYaml.load(zoneFile, plugin.getLogger());
        for (String name : yaml.getKeys(false)) {
            ConfigurationSection s = yaml.getConfigurationSection(name);
            if (s == null || s.getString("world") == null) continue;
            zones.put(name.toLowerCase(Locale.ROOT), new KothZone(name.toLowerCase(Locale.ROOT), s.getString("world"),
                    s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), Math.max(1, s.getDouble("radius", 5)),
                    Math.max(1, s.getDouble("half-height", 10))));
        }
    }

    private void saveZones() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (KothZone zone : zones.values()) {
            String base = zone.name();
            yaml.set(base + ".world", zone.world());
            yaml.set(base + ".x", zone.x());
            yaml.set(base + ".y", zone.y());
            yaml.set(base + ".z", zone.z());
            yaml.set(base + ".radius", zone.radius());
            yaml.set(base + ".half-height", zone.halfHeight());
        }
        try {
            com.stratasmp.stratacore.AtomicYaml.save(yaml, zoneFile);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Could not save koth-zones.yml: " + e.getMessage());
        }
    }

    String setZone(String name, Location at, double radius, double halfHeight) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!key.matches("[a-z0-9_-]{1,24}")) return "Zone names are 1-24 letters, numbers, - or _.";
        if (active != null && active.zone.name().equals(key)) return "That zone is in use right now.";
        zones.put(key, new KothZone(key, at.getWorld().getName(), at.getBlockX() + 0.5, at.getBlockY(), at.getBlockZ() + 0.5,
                Math.max(1, radius), Math.max(1, halfHeight)));
        saveZones();
        return null;
    }

    boolean deleteZone(String name) {
        KothZone removed = zones.remove(name.toLowerCase(Locale.ROOT));
        if (removed != null) saveZones();
        return removed != null;
    }

    List<KothZone> zones() {
        return new ArrayList<>(zones.values());
    }

    /* ---- running ---- */

    boolean running() {
        return active != null;
    }

    /** @return null on success, otherwise why it didn't start. */
    String start(String zoneName) {
        if (active != null) return "A KOTH is already running.";
        KothZone zone;
        if (zoneName == null || zoneName.isBlank()) {
            if (zones.isEmpty()) return "No KOTH zones are set. Use /koth setzone <name> <radius>.";
            List<KothZone> all = zones();
            zone = all.get(ThreadLocalRandom.current().nextInt(all.size()));
        } else {
            zone = zones.get(zoneName.toLowerCase(Locale.ROOT));
            if (zone == null) return "No zone called " + zoneName + ".";
        }
        if (Bukkit.getWorld(zone.world()) == null) return "The world " + zone.world() + " isn't loaded.";
        ConfigurationSection cfg = cfg();
        int target = cfg == null ? 120 : cfg.getInt("capture-seconds", 120);
        HillState hill = new HillState(target, cfg == null || cfg.getBoolean("drain-when-empty", true));
        BossBar bar = BossBar.bossBar(Component.text("KOTH " + zone.name() + " - nobody is capturing", NamedTextColor.YELLOW),
                0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        active = new Active(zone, hill, bar);
        Bukkit.getServer().showBossBar(bar);
        Bukkit.broadcast(Component.text("[KOTH] " + zone.name() + " is open at X " + Math.round(zone.x()) + ", Z " + Math.round(zone.z())
                + " in " + zone.world() + "! Hold it alone for " + target + "s to win.", NamedTextColor.GOLD));
        return null;
    }

    void stop(String reason) {
        if (active == null) return;
        Bukkit.getServer().hideBossBar(active.bar);
        if (reason != null) Bukkit.broadcast(Component.text("[KOTH] " + reason, NamedTextColor.GOLD));
        active = null;
    }

    void shutdown() {
        stop(null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (active != null) event.getPlayer().showBossBar(active.bar);
    }

    /** Called every second. */
    void tick() {
        ConfigurationSection cfg = cfg();
        if (cfg == null || !cfg.getBoolean("enabled", true)) {
            if (active != null) stop("The KOTH was switched off.");
            return;
        }
        if (active == null) {
            checkSchedule(cfg);
            return;
        }
        Active run = active;
        if (System.currentTimeMillis() - run.startedAt > cfg.getLong("max-minutes", 15) * 60_000L) {
            stop("Time's up - nobody held " + run.zone.name() + " long enough.");
            return;
        }
        Map<String, List<Player>> inside = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            GameMode mode = player.getGameMode();
            if (player.isDead() || mode == GameMode.SPECTATOR || mode == GameMode.CREATIVE) continue;
            Location at = player.getLocation();
            if (run.zone.contains(at.getWorld().getName(), at.getX(), at.getY(), at.getZ())) {
                inside.computeIfAbsent(holderKey(player), k -> new ArrayList<>()).add(player);
            }
        }
        HillState.Status status = run.hill.tick(inside.keySet());
        update(run, status, inside);
        if (status == HillState.Status.WON) win(run, inside.getOrDefault(run.hill.capturer(), List.of()));
    }

    private void update(Active run, HillState.Status status, Map<String, List<Player>> inside) {
        String who = run.hill.capturer() == null ? null : displayName(run.hill.capturer(), inside);
        run.bar.progress(Math.min(1f, run.hill.progress() / (float) run.hill.target()));
        run.bar.name(Component.text(who == null
                ? "KOTH " + run.zone.name() + " - nobody is capturing"
                : "KOTH " + run.zone.name() + " - " + who + " " + run.hill.progress() + "/" + run.hill.target() + "s"
                + (status == HillState.Status.CONTESTED ? " (contested)" : ""), NamedTextColor.YELLOW));
        if (status == HillState.Status.CONTESTED && run.lastStatus != HillState.Status.CONTESTED) {
            Bukkit.broadcast(Component.text("[KOTH] " + run.zone.name() + " is contested!", NamedTextColor.RED));
        }
        if (who != null && !who.equals(run.lastCapturer) && status == HillState.Status.CAPTURING) {
            Bukkit.broadcast(Component.text("[KOTH] " + who + " started capturing " + run.zone.name() + ".", NamedTextColor.YELLOW));
        }
        ConfigurationSection cfg = cfg();
        int every = Math.max(5, cfg.getInt("announce-every-seconds", 30));
        if (status == HillState.Status.CAPTURING && who != null && run.hill.progress() % every == 0 && run.hill.progress() > 0) {
            Bukkit.broadcast(Component.text("[KOTH] " + who + " holds " + run.zone.name() + ": " + run.hill.progress() + "/" + run.hill.target() + "s", NamedTextColor.YELLOW));
        }
        if (who != null) run.lastCapturer = who;
        run.lastStatus = status;
    }

    private void win(Active run, List<Player> winners) {
        String who = winners.isEmpty() ? "Someone" : displayName(run.hill.capturer(), Map.of(run.hill.capturer(), winners));
        Bukkit.broadcast(Component.text("[KOTH] " + who + " captured " + run.zone.name() + "!", NamedTextColor.GOLD));
        ConfigurationSection rewards = cfg().getConfigurationSection("rewards");
        for (Player winner : winners) pay(winner, rewards);
        stop(null);
    }

    private void pay(Player player, ConfigurationSection rewards) {
        if (rewards == null) return;
        List<String> lines = new ArrayList<>();
        long stratas = rewards.getLong("stratas", 0);
        StrataEconomy economy = plugin.core().module(StrataEconomy.class);
        if (stratas > 0 && economy != null && economy.isEnabled()) {
            economy.stratas().deposit(player.getUniqueId(), stratas);
            lines.add(economy.money(stratas));
        }
        List<KeystoneRewards.Drop> drops = new ArrayList<>();
        for (String spec : rewards.getStringList("items")) {
            KeystoneRewards.Drop drop = KeystoneRewards.parse(spec);
            if (drop == null) plugin.getLogger().warning("Bad KOTH reward entry: \"" + spec + "\"");
            else drops.add(drop);
        }
        for (KeystoneRewards.Stack stack : KeystoneRewards.rollDrops(drops, ThreadLocalRandom.current(), org.bukkit.Material::getMaxStackSize)) {
            ItemStack item = stack.toItem();
            for (ItemStack left : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
            lines.add(stack.amount() + "x " + stack.material().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        for (String command : rewards.getStringList("commands")) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("KOTH reward command failed for " + player.getName() + ": " + command + " (" + e + ")");
            }
        }
        if (!lines.isEmpty()) player.sendMessage(Component.text("KOTH reward: " + String.join(", ", lines), NamedTextColor.GOLD));
    }

    private void checkSchedule(ConfigurationSection cfg) {
        ZonedDateTime now = ZonedDateTime.now(plugin.zone());
        for (EventSchedule.Slot slot : WeeklyBossFeature.slots(cfg.getStringList("slots"))) {
            long until = EventSchedule.minutesUntil(slot, now);
            for (int minutes : cfg.getIntegerList("warn-minutes")) {
                if (until == minutes && warned.add("koth:" + now.toLocalDate() + slot + minutes)) {
                    Bukkit.broadcast(Component.text("[KOTH] King of the Hill starts in " + minutes + " minute" + (minutes == 1 ? "" : "s") + "!", NamedTextColor.GOLD));
                }
            }
            String key = EventSchedule.dueKey("koth", slot, now, cfg.getInt("catch-up-minutes", 15));
            if (key == null || plugin.alreadyFired(key)) continue;
            if (Bukkit.getOnlinePlayers().size() < cfg.getInt("min-online", 2)) continue;
            String problem = start(null);
            if (problem == null) {
                plugin.markFired(key);
            } else if (loggedNoZones.add(key)) {
                plugin.getLogger().warning("Scheduled KOTH did not start: " + problem);
            }
        }
    }

    /** Team members capture together; everyone else captures alone. */
    private String holderKey(Player player) {
        StrataTeams teams = plugin.core().module(StrataTeams.class);
        if (teams != null && teams.getTeamManager() != null) {
            Team team = teams.getTeamManager().getTeam(player.getUniqueId());
            if (team != null) return "team:" + team.id;
        }
        return "player:" + player.getUniqueId();
    }

    private String displayName(String holder, Map<String, List<Player>> inside) {
        if (holder.startsWith("team:")) {
            StrataTeams teams = plugin.core().module(StrataTeams.class);
            List<Player> members = inside.get(holder);
            if (teams != null && teams.getTeamManager() != null && members != null && !members.isEmpty()) {
                Team team = teams.getTeamManager().getTeam(members.get(0).getUniqueId());
                if (team != null) return "Team " + team.name;
            }
            return "A team";
        }
        List<Player> members = inside.get(holder);
        if (members != null && !members.isEmpty()) return members.get(0).getName();
        Player online = Bukkit.getPlayer(java.util.UUID.fromString(holder.substring("player:".length())));
        return online == null ? "A player" : online.getName();
    }

    String status() {
        if (active == null) return "No KOTH is running.";
        return "KOTH " + active.zone.name() + " at X " + Math.round(active.zone.x()) + ", Z " + Math.round(active.zone.z())
                + " in " + active.zone.world() + " - " + active.hill.progress() + "/" + active.hill.target() + "s";
    }
}
