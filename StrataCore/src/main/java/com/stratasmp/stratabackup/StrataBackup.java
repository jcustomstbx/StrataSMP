package com.stratasmp.stratabackup;

import com.stratasmp.strataeconomy.StrataEconomy;
import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * /backup and the 3-day schedule. The game server runs in a container and cannot reach the database or the
 * backup folder itself, so it pauses saving, drops a request file, and a root script on the VPS (started by a
 * systemd path unit) does the work and writes a result file. Saving is always switched back on afterwards.
 */
public final class StrataBackup extends StrataModule {

    private static final TextColor BLUE = TextColor.fromHexString("#37B6FF");

    private enum Kind { FULL, QUICK }

    private static final class Run {
        final String id = Long.toString(System.currentTimeMillis());
        final Kind kind;
        final String by;
        final long startedAt = System.currentTimeMillis();
        boolean pickedUp;

        Run(Kind kind, String by) {
            this.kind = kind;
            this.by = by;
        }
    }

    private File requestFile;
    private File resultFile;
    private File stateFile;
    private Run current;
    private BukkitTask poll;

    public StrataBackup(StrataCore core) {
        super(core, "StrataBackup");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        File dir = getDataFolder();
        requestFile = new File(dir, "request.properties");
        resultFile = new File(dir, "result.properties");
        stateFile = new File(dir, "state.yml");
        getCommand("backup").setExecutor(this);
        getCommand("backup").setTabCompleter(this);
        // a leftover result from before a restart is simply discarded; saving is on again after a restart anyway
        resultFile.delete();
        getServer().getScheduler().runTaskTimer(this, this::scheduleTick, 20L * 60, 20L * 60);
    }

    @Override
    public void onDisable() {
        if (poll != null) {
            poll.cancel();
        }
        if (current != null) {
            resume();
            current = null;
        }
        super.onDisable();
    }

    // ---- command ----

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("strata.backup")) {
            sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return true;
        }
        String arg = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (arg) {
            case "status" -> status(sender);
            case "quick" -> start(sender, Kind.QUICK);
            case "full" -> start(sender, Kind.FULL);
            case "" -> start(sender, "quick".equalsIgnoreCase(getConfig().getString("command-default", "full")) ? Kind.QUICK : Kind.FULL);
            default -> sender.sendMessage(Component.text("Usage: /backup [full|quick|status]", NamedTextColor.YELLOW));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 && sender.hasPermission("strata.backup") ? List.of("full", "quick", "status") : List.of();
    }

    private void status(CommandSender sender) {
        if (current != null) {
            long seconds = (System.currentTimeMillis() - current.startedAt) / 1000;
            send(sender, "A " + current.kind.name().toLowerCase(Locale.ROOT) + " backup is running (" + seconds + "s so far, started by "
                    + current.by + ").", NamedTextColor.YELLOW);
            return;
        }
        YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile);
        String last = state.getString("last.summary");
        send(sender, last == null ? "No backup has run yet." : "Last backup: " + last, BLUE);
    }

    // ---- scheduling ----

    private void scheduleTick() {
        if (current != null || !getConfig().getBoolean("schedule.enabled", true)) {
            return;
        }
        ZoneId zone;
        LocalTime at;
        try {
            zone = ZoneId.of(getConfig().getString("schedule.timezone", "UTC"));
            at = LocalTime.parse(getConfig().getString("schedule.time", "04:30"));
        } catch (RuntimeException e) {
            getLogger().warning("Bad schedule.timezone or schedule.time, scheduled backups are skipped.");
            return;
        }
        YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile);
        String lastText = state.getString("last.date");
        LocalDate last = null;
        if (lastText != null) {
            try {
                last = LocalDate.parse(lastText);
            } catch (RuntimeException ignored) {
                // treated as never run
            }
        }
        if (BackupSchedule.isDue(LocalDateTime.now(zone), last, getConfig().getInt("schedule.every-days", 3), at)) {
            Kind kind = "quick".equalsIgnoreCase(getConfig().getString("schedule.type", "full")) ? Kind.QUICK : Kind.FULL;
            getLogger().info("Scheduled " + kind.name().toLowerCase(Locale.ROOT) + " backup starting.");
            start(Bukkit.getConsoleSender(), kind);
        }
    }

    // ---- the backup itself ----

    private void start(CommandSender by, Kind kind) {
        if (current != null) {
            send(by, "A backup is already running.", NamedTextColor.YELLOW);
            return;
        }
        Run run = new Run(kind, BackupSchedule.safe(by.getName()));
        current = run;
        recordAttempt();
        send(by, "Backup started (" + kind.name().toLowerCase(Locale.ROOT) + "). Saving the world first...", BLUE);
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            flushEconomy();
            getServer().getScheduler().runTask(this, () -> begin(run, by));
        });
    }

    private void flushEconomy() {
        try {
            StrataEconomy economy = core().module(StrataEconomy.class);
            if (economy != null && economy.stratas() != null) {
                economy.stratas().flushNow();
            }
        } catch (RuntimeException e) {
            getLogger().warning("Could not flush balances before the backup: " + e.getMessage());
        }
    }

    private void begin(Run run, CommandSender by) {
        if (current != run) {
            return;
        }
        boolean chunky = getConfig().getBoolean("chunky-pause", true) && getServer().getPluginManager().getPlugin("Chunky") != null;
        if (chunky) {
            console("chunky pause");
        }
        console("save-all flush");
        console("save-off");
        try {
            writeRequest(run);
        } catch (IOException e) {
            getLogger().severe("Could not write the backup request: " + e.getMessage());
            finish(run, by, false, "could not write the request file");
            return;
        }
        long timeoutMs = Math.max(5, getConfig().getInt("total-timeout-minutes", 120)) * 60_000L;
        long pickupMs = Math.max(10, getConfig().getInt("pickup-timeout-seconds", 120)) * 1000L;
        poll = getServer().getScheduler().runTaskTimer(this, () -> {
            if (current != run) {
                return;
            }
            if (resultFile.exists()) {
                Properties result = readResult();
                if (result != null && run.id.equals(result.getProperty("id"))) {
                    boolean ok = "ok".equals(result.getProperty("status"));
                    finish(run, by, ok, summary(result));
                    return;
                }
            }
            long age = System.currentTimeMillis() - run.startedAt;
            if (requestFile.exists() && age > pickupMs) {
                requestFile.delete();
                finish(run, by, false, "the backup service on the server didn't start (is strata-backup.path active?)");
            } else if (age > timeoutMs) {
                finish(run, by, false, "timed out waiting for the backup to finish");
            }
        }, 40L, 40L);
    }

    private void writeRequest(Run run) throws IOException {
        File dir = requestFile.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        Properties p = new Properties();
        p.setProperty("id", run.id);
        p.setProperty("type", run.kind.name().toLowerCase(Locale.ROOT));
        p.setProperty("by", run.by);
        p.setProperty("keep.database", Integer.toString(clamp(getConfig().getInt("keep.database", 10))));
        p.setProperty("keep.data", Integer.toString(clamp(getConfig().getInt("keep.data", 10))));
        p.setProperty("keep.world", Integer.toString(clamp(getConfig().getInt("keep.world", 1))));
        File tmp = new File(dir, "request.properties.tmp");
        try (OutputStream out = Files.newOutputStream(tmp.toPath())) {
            p.store(out, null);
        }
        Files.move(tmp.toPath(), requestFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static int clamp(int keep) {
        return Math.max(1, Math.min(60, keep));
    }

    private Properties readResult() {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(resultFile.toPath())) {
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            return p;
        } catch (IOException e) {
            return null;
        }
    }

    private String summary(Properties r) {
        StringBuilder sb = new StringBuilder();
        sb.append("database ").append(r.getProperty("database", "?"));
        sb.append(", data ").append(r.getProperty("data", "?"));
        sb.append(", world ").append(r.getProperty("world", "?"));
        sb.append(" - ").append(r.getProperty("seconds", "?")).append("s");
        String note = r.getProperty("note");
        if (note != null && !note.isEmpty()) {
            sb.append(" (").append(note).append(')');
        }
        return sb.toString();
    }

    private void finish(Run run, CommandSender by, boolean ok, String detail) {
        if (poll != null) {
            poll.cancel();
            poll = null;
        }
        resultFile.delete();
        resume();
        current = null;
        String text = (ok ? "Backup complete: " : "Backup FAILED: ") + detail;
        send(by, text, ok ? NamedTextColor.GREEN : NamedTextColor.RED);
        if (!(by instanceof org.bukkit.command.ConsoleCommandSender)) {
            getLogger().info(text);
        }
        YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile);
        state.set("last.summary", (ok ? "ok - " : "FAILED - ") + detail + " [" + LocalDateTime.now().withNano(0) + "]");
        saveState(state);
    }

    private void resume() {
        console("save-on");
        if (getConfig().getBoolean("chunky-pause", true) && getServer().getPluginManager().getPlugin("Chunky") != null) {
            console("chunky continue");
        }
    }

    private void recordAttempt() {
        YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile);
        String zoneId = getConfig().getString("schedule.timezone", "UTC");
        ZoneId zone;
        try {
            zone = ZoneId.of(zoneId);
        } catch (RuntimeException e) {
            zone = ZoneId.of("UTC");
        }
        state.set("last.date", LocalDate.now(zone).toString());
        saveState(state);
    }

    private void saveState(YamlConfiguration state) {
        try {
            File dir = stateFile.getParentFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            state.save(stateFile);
        } catch (IOException e) {
            getLogger().warning("Could not save backup state: " + e.getMessage());
        }
    }

    private void console(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private void send(CommandSender to, String text, TextColor colour) {
        to.sendMessage(Component.text("[Backup] ", BLUE).append(Component.text(text, colour)));
    }
}
