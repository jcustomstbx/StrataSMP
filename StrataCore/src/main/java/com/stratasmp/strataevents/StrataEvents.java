package com.stratasmp.strataevents;

import com.stratasmp.stratacore.AsyncYamlSaver;
import com.stratasmp.stratacore.AtomicYaml;
import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;
import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Scheduled and daily activities that pay out Stratas: login streaks, a weekly boss and King of the Hill. */
public class StrataEvents extends StrataModule {
    private static final int KEEP_FIRED = 100;

    private final Set<String> fired = new LinkedHashSet<>();
    private AsyncYamlSaver stateSaver;
    private ZoneId zone = ZoneId.systemDefault();
    private LoginFeature login;
    private WeeklyBossFeature weeklyBoss;
    private KothFeature koth;

    public StrataEvents(StrataCore core) {
        super(core, "StrataEvents");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        applyConfig();
        File stateFile = new File(getDataFolder(), "events-state.yml");
        stateSaver = new AsyncYamlSaver(this, stateFile);
        fired.addAll(AtomicYaml.load(stateFile, getLogger()).getStringList("fired"));
        login = new LoginFeature(this);
        weeklyBoss = new WeeklyBossFeature(this);
        koth = new KothFeature(this);
        getServer().getPluginManager().registerEvents(login, this);
        getServer().getPluginManager().registerEvents(koth, this);
        for (String name : List.of("koth", "daily", "events")) {
            if (getCommand(name) != null) getCommand(name).setExecutor(this);
        }
        getServer().getScheduler().runTaskTimer(this, koth::tick, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, weeklyBoss::tick, 200L, 400L);
    }

    @Override
    public void onDisable() {
        if (koth != null) koth.shutdown();
        super.onDisable();
    }

    private void applyConfig() {
        reloadConfig();
        try {
            String name = getConfig().getString("timezone", "");
            zone = name == null || name.isBlank() ? ZoneId.systemDefault() : ZoneId.of(name);
        } catch (java.time.DateTimeException e) {
            getLogger().warning("Unknown timezone in config; using the server's.");
            zone = ZoneId.systemDefault();
        }
    }

    ZoneId zone() {
        return zone;
    }

    LocalDate today() {
        return LocalDate.now(zone);
    }

    boolean alreadyFired(String key) {
        return fired.contains(key);
    }

    void markFired(String key) {
        fired.add(key);
        while (fired.size() > KEEP_FIRED) {
            fired.remove(fired.iterator().next());
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("fired", new ArrayList<>(fired));
        stateSaver.save(yaml);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "daily" -> {
                if (sender instanceof Player player) login.showStatus(player);
                else sender.sendMessage("Only players have a login streak.");
            }
            case "koth" -> kothCommand(sender, args);
            default -> eventsCommand(sender, args);
        }
        return true;
    }

    private void eventsCommand(CommandSender sender, String[] args) {
        if (args.length >= 1 && sender.hasPermission("strataevents.admin")) {
            if (args[0].equalsIgnoreCase("reload")) {
                applyConfig();
                koth.loadZones();
                sender.sendMessage(Component.text("StrataEvents reloaded.", NamedTextColor.GREEN));
                return;
            }
            if (args[0].equalsIgnoreCase("boss")) {
                boolean ok = weeklyBoss.spawn(getConfig().getString("weekly-boss.boss", ""));
                sender.sendMessage(Component.text(ok ? "Boss spawned." : "No boss could spawn (needs an eligible player and a safe spot).",
                        ok ? NamedTextColor.GREEN : NamedTextColor.RED));
                return;
            }
        }
        ZonedDateTime now = ZonedDateTime.now(zone);
        sender.sendMessage(Component.text("Upcoming events:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(" Weekly boss: " + nextText("weekly-boss", now), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(" King of the Hill: " + nextText("koth", now), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(" " + koth.status(), NamedTextColor.GRAY));
    }

    private String nextText(String section, ZonedDateTime now) {
        if (!getConfig().getBoolean(section + ".enabled", true)) return "off";
        ZonedDateTime best = null;
        for (EventSchedule.Slot slot : WeeklyBossFeature.slots(getConfig().getStringList(section + ".slots"))) {
            ZonedDateTime next = EventSchedule.next(slot, now);
            if (best == null || next.isBefore(best)) best = next;
        }
        if (best == null) return "not scheduled";
        Duration until = Duration.between(now, best);
        long hours = until.toHours();
        return best.getDayOfWeek().toString().charAt(0) + best.getDayOfWeek().toString().substring(1).toLowerCase(Locale.ROOT)
                + " " + best.toLocalTime() + " (in " + (hours >= 24 ? hours / 24 + "d " + hours % 24 + "h" : hours + "h " + until.toMinutesPart() + "m") + ")";
    }

    private void kothCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || !sender.hasPermission("strataevents.admin")) {
            sender.sendMessage(Component.text(koth.status(), NamedTextColor.YELLOW));
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                String problem = koth.start(args.length >= 2 ? args[1] : null);
                if (problem != null) sender.sendMessage(Component.text(problem, NamedTextColor.RED));
            }
            case "stop" -> {
                if (koth.running()) koth.stop("The KOTH was stopped by an admin.");
                else sender.sendMessage(Component.text("No KOTH is running.", NamedTextColor.RED));
            }
            case "setzone" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Run this in-game, standing at the centre.");
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("/koth setzone <name> <radius> [half-height]", NamedTextColor.GRAY));
                    return;
                }
                try {
                    double radius = Double.parseDouble(args[2]);
                    double halfHeight = args.length >= 4 ? Double.parseDouble(args[3]) : 10;
                    String problem = koth.setZone(args[1], player.getLocation(), radius, halfHeight);
                    sender.sendMessage(problem == null
                            ? Component.text("Zone " + args[1].toLowerCase(Locale.ROOT) + " saved at your position.", NamedTextColor.GREEN)
                            : Component.text(problem, NamedTextColor.RED));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Radius and height must be numbers.", NamedTextColor.RED));
                }
            }
            case "delzone" -> sender.sendMessage(args.length >= 2 && koth.deleteZone(args[1])
                    ? Component.text("Zone deleted.", NamedTextColor.GREEN) : Component.text("No such zone.", NamedTextColor.RED));
            case "list" -> {
                for (KothZone z : koth.zones()) {
                    sender.sendMessage(Component.text(z.name() + ": " + z.world() + " " + Math.round(z.x()) + "," + Math.round(z.y()) + ","
                            + Math.round(z.z()) + " r=" + z.radius() + " h=" + z.halfHeight(), NamedTextColor.YELLOW));
                }
                if (koth.zones().isEmpty()) sender.sendMessage(Component.text("No zones yet.", NamedTextColor.GRAY));
            }
            default -> sender.sendMessage(Component.text("/koth [start [zone]|stop|setzone <name> <radius> [half-height]|delzone <name>|list]", NamedTextColor.GRAY));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("strataevents.admin")) return List.of();
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("koth")) {
            if (args.length == 1) return List.of("start", "stop", "setzone", "delzone", "list");
            if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("delzone"))) {
                return koth.zones().stream().map(KothZone::name).toList();
            }
        }
        if (name.equals("events") && args.length == 1) return List.of("reload", "boss");
        return List.of();
    }
}
