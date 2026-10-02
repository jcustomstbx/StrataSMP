package com.stratasmp.strataleaderboards;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The placed boards, kept in boards.yml so they survive a restart. */
public final class BoardStore {

    private final File file;
    private final Map<String, Board> boards = new LinkedHashMap<>();

    public BoardStore(File file) {
        this.file = file;
    }

    public synchronized void load() {
        boards.clear();
        var config = YamlConfiguration.loadConfiguration(file);
        var section = config.getConfigurationSection("boards");
        if (section == null) {
            return;
        }
        for (var id : section.getKeys(false)) {
            var type = BoardType.parse(section.getString(id + ".type"));
            if (type.isEmpty()) {
                continue;
            }
            UUID entity = null;
            var rawEntity = section.getString(id + ".entity");
            if (rawEntity != null && !rawEntity.isBlank()) {
                try {
                    entity = UUID.fromString(rawEntity);
                } catch (IllegalArgumentException ignored) {
                    // a damaged entity id is dropped and the board simply spawns a new one
                }
            }
            boards.put(id, new Board(id, type.get(), section.getString(id + ".world", "world"),
                section.getDouble(id + ".x"), section.getDouble(id + ".y"), section.getDouble(id + ".z"),
                (float) section.getDouble(id + ".scale", 1.0), (float) section.getDouble(id + ".yaw", 0.0), entity));
        }
    }

    public synchronized List<Board> all() {
        return new ArrayList<>(boards.values());
    }

    public synchronized Optional<Board> find(String id) {
        return Optional.ofNullable(boards.get(id));
    }

    public synchronized void put(Board board) {
        boards.put(board.id(), board);
        save();
    }

    public synchronized void remove(String id) {
        boards.remove(id);
        save();
    }

    /** The next free id for a type, such as kills-1, kills-2. */
    public synchronized String nextId(BoardType type) {
        var number = 1;
        while (boards.containsKey(type.id() + "-" + number)) {
            number++;
        }
        return type.id() + "-" + number;
    }

    private void save() {
        var config = new YamlConfiguration();
        for (var board : boards.values()) {
            var path = "boards." + board.id();
            config.set(path + ".type", board.type().id());
            config.set(path + ".world", board.world());
            config.set(path + ".x", board.x());
            config.set(path + ".y", board.y());
            config.set(path + ".z", board.z());
            config.set(path + ".scale", (double) board.scale());
            config.set(path + ".yaw", (double) board.yaw());
            config.set(path + ".entity", board.entityId() == null ? null : board.entityId().toString());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save boards.yml", e);
        }
    }
}
