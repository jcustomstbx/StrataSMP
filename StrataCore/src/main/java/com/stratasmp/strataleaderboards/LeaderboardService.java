package com.stratasmp.strataleaderboards;

import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Places boards, keeps their text up to date and cleans up the text displays that draw them. */
public final class LeaderboardService {

    static final String TAG_PREFIX = "stratasmp_leaderboard_";

    private final Plugin plugin;
    private final BoardStore store;
    private final StatsRepository repository;
    private final StreakTracker streaks;

    public LeaderboardService(Plugin plugin, BoardStore store, StatsRepository repository, StreakTracker streaks) {
        this.plugin = plugin;
        this.store = store;
        this.repository = repository;
        this.streaks = streaks;
    }

    private int rows() {
        return Math.max(1, Math.min(20, plugin.getConfig().getInt("rows", 10)));
    }

    private Key font() {
        var name = plugin.getConfig().getString("font", "").trim();
        return name.isEmpty() ? null : Key.key(name);
    }

    /** location's own yaw is the direction the board faces; the caller decides what that should be. */
    public Board create(Location location, BoardType type, float scale) {
        var board = new Board(store.nextId(type), type, location.getWorld().getName(), location.getX(), location.getY(),
            location.getZ(), scale, location.getYaw(), null);
        store.put(board);
        refreshAll();
        return board;
    }

    public boolean remove(String id) {
        var board = store.find(id);
        if (board.isEmpty()) {
            return false;
        }
        findEntity(board.get()).ifPresent(TextDisplay::remove);
        store.remove(id);
        return true;
    }

    public boolean move(String id, Location location) {
        var board = store.find(id);
        if (board.isEmpty()) {
            return false;
        }
        findEntity(board.get()).ifPresent(TextDisplay::remove);
        store.put(board.get().movedTo(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
            location.getYaw()).withEntity(null));
        refreshAll();
        return true;
    }

    /** Same spot and facing, just bigger or smaller text, for when a board doesn't fit the wall it's mounted on. */
    public boolean resize(String id, float scale) {
        var board = store.find(id);
        if (board.isEmpty()) {
            return false;
        }
        findEntity(board.get()).ifPresent(TextDisplay::remove);
        store.put(board.get().resized(scale).withEntity(null));
        refreshAll();
        return true;
    }

    public List<Board> boards() {
        return store.all();
    }

    /** Reads every board's data off the main thread, then redraws them all. */
    public void refreshAll() {
        var boards = store.all();
        if (boards.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            var byType = new EnumMap<BoardType, List<Row>>(BoardType.class);
            for (var board : boards) {
                byType.computeIfAbsent(board.type(), type -> named(repository.top(type, rows(), streaks)));
            }
            Bukkit.getScheduler().runTask(plugin, () -> boards.forEach(board -> draw(board, byType.get(board.type()))));
        });
    }

    private List<Row> named(List<Row> rows) {
        return rows.stream().map(row -> {
            if (row.name() != null && !row.name().isBlank()) {
                return row;
            }
            var name = Bukkit.getOfflinePlayer(row.playerId()).getName();
            return row.withName(name == null ? "Unknown" : name);
        }).toList();
    }

    private void draw(Board board, List<Row> rows) {
        var world = Bukkit.getWorld(board.world());
        if (world == null || !world.isChunkLoaded((int) Math.floor(board.x()) >> 4, (int) Math.floor(board.z()) >> 4)) {
            return;
        }
        var display = findEntity(board).orElseGet(() -> spawn(world, board));
        display.text(BoardRenderer.render(board.type(), rows, font()));
        if (board.entityId() == null || !board.entityId().equals(display.getUniqueId())) {
            store.put(board.withEntity(display.getUniqueId()));
        }
    }

    private Optional<TextDisplay> findEntity(Board board) {
        if (board.entityId() != null && Bukkit.getEntity(board.entityId()) instanceof TextDisplay display && !display.isDead()) {
            return Optional.of(display);
        }
        var world = Bukkit.getWorld(board.world());
        if (world == null) {
            return Optional.empty();
        }
        // The stored id can go stale, so look for the board's own tag near where it should be before making another.
        var tag = TAG_PREFIX + board.id();
        return world.getNearbyEntities(new Location(world, board.x(), board.y(), board.z()), 2, 2, 2).stream()
            .filter(entity -> entity instanceof TextDisplay && entity.getScoreboardTags().contains(tag))
            .map(entity -> (TextDisplay) entity)
            .findFirst();
    }

    private TextDisplay spawn(World world, Board board) {
        return world.spawn(new Location(world, board.x(), board.y(), board.z(), board.yaw(), 0f), TextDisplay.class, display -> {
            display.addScoreboardTag(TAG_PREFIX + board.id());
            // FIXED, not VERTICAL: a board mounted flat on a wall shouldn't swing round to face whoever walks past.
            display.setBillboard(Display.Billboard.FIXED);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setLineWidth(600);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setShadowed(true);
            display.setSeeThrough(false);
            display.setViewRange(2.0f);
            display.setPersistent(true);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                new Vector3f(board.scale(), board.scale(), board.scale()), new AxisAngle4f()));
        });
    }
}
