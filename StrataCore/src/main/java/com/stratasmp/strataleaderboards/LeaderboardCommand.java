package com.stratasmp.strataleaderboards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class LeaderboardCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final LeaderboardService service;

    LeaderboardCommand(Plugin plugin, LeaderboardService service) {
        this.plugin = plugin;
        this.service = service;
    }

    private static void say(CommandSender sender, String text, NamedTextColor colour) {
        sender.sendMessage(Component.text(text, colour));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create" -> create(sender, args);
            case "remove" -> {
                if (args.length < 2) {
                    say(sender, "Usage: /leaderboard remove <id>", NamedTextColor.YELLOW);
                } else if (service.remove(args[1])) {
                    say(sender, "Removed " + args[1] + ".", NamedTextColor.GREEN);
                } else {
                    say(sender, "There is no board called " + args[1] + ". Try /leaderboard list.", NamedTextColor.RED);
                }
            }
            case "move" -> move(sender, args);
            case "resize" -> resize(sender, args);
            case "list" -> {
                if (service.boards().isEmpty()) {
                    say(sender, "No boards yet. Stand where you want one and use /leaderboard create <kills|streak|deaths|playtime|stratas>.", NamedTextColor.YELLOW);
                }
                service.boards().forEach(board -> say(sender, board.id() + "  in " + board.world() + " at "
                    + Math.round(board.x()) + ", " + Math.round(board.y()) + ", " + Math.round(board.z()), NamedTextColor.GRAY));
            }
            case "refresh" -> {
                service.refreshAll();
                say(sender, "Refreshing every board.", NamedTextColor.GREEN);
            }
            default -> say(sender, "Usage: /leaderboard create <kills|streak|deaths|playtime|stratas> [scale] | remove <id> | move <id> | resize <id> <scale> | list | refresh",
                NamedTextColor.YELLOW);
        }
        return true;
    }

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            say(sender, "Stand in the world and run this as a player.", NamedTextColor.RED);
            return;
        }
        var type = args.length > 1 ? BoardType.parse(args[1]) : Optional.<BoardType>empty();
        if (type.isEmpty()) {
            say(sender, "Pick a board: kills, streak, deaths, playtime or stratas.", NamedTextColor.RED);
            return;
        }
        var scale = (float) plugin.getConfig().getDouble("default-scale", 1.0);
        if (args.length > 2) {
            try {
                scale = Math.max(0.2f, Math.min(6.0f, Float.parseFloat(args[2])));
            } catch (NumberFormatException e) {
                say(sender, args[2] + " isn't a size. Try something like 1.5.", NamedTextColor.RED);
                return;
            }
        }
        var board = service.create(placement(player), type.get(), scale);
        say(sender, "Placed " + board.id() + ". Use /leaderboard move " + board.id() + " to shift it.", NamedTextColor.GREEN);
    }

    private void move(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length < 2) {
            say(sender, "Usage, as a player: /leaderboard move <id>", NamedTextColor.YELLOW);
            return;
        }
        if (service.move(args[1], placement(player))) {
            say(sender, "Moved " + args[1] + ".", NamedTextColor.GREEN);
        } else {
            say(sender, "There is no board called " + args[1] + ".", NamedTextColor.RED);
        }
    }

    private void resize(CommandSender sender, String[] args) {
        if (args.length < 3) {
            say(sender, "Usage: /leaderboard resize <id> <scale>", NamedTextColor.YELLOW);
            return;
        }
        float scale;
        try {
            scale = Math.max(0.2f, Math.min(6.0f, Float.parseFloat(args[2])));
        } catch (NumberFormatException e) {
            say(sender, args[2] + " isn't a size. Try something like 0.6 if the text is running off the edge.", NamedTextColor.RED);
            return;
        }
        if (service.resize(args[1], scale)) {
            say(sender, "Resized " + args[1] + " to " + scale + ".", NamedTextColor.GREEN);
        } else {
            say(sender, "There is no board called " + args[1] + ".", NamedTextColor.RED);
        }
    }

    private static final double WALL_RAY_DISTANCE = 6.0;
    // Pulled just off the surface so the board doesn't z-fight with the wall texture behind it.
    private static final double WALL_OFFSET = 0.02;

    /**
     * Looking at a wall within range mounts the board flush on that block's face, facing out of it, like a
     * picture frame. Otherwise it falls back to floating where the player stands, facing back towards them.
     */
    private Location placement(Player player) {
        var eye = player.getEyeLocation();
        var hit = eye.getWorld().rayTraceBlocks(eye, eye.getDirection(), WALL_RAY_DISTANCE);
        if (hit != null && hit.getHitBlock() != null && hit.getHitBlockFace() != null) {
            var face = hit.getHitBlockFace();
            var location = hit.getHitPosition().toLocation(eye.getWorld())
                .add(face.getDirection().multiply(WALL_OFFSET));
            location.setDirection(face.getDirection());
            return location;
        }
        var location = player.getLocation().add(0, 1.5, 0);
        location.setYaw(location.getYaw() + 180f);
        return location;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        var options = new ArrayList<String>();
        if (args.length == 1) {
            options.addAll(List.of("create", "remove", "move", "resize", "list", "refresh"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            Arrays.stream(BoardType.values()).forEach(type -> options.add(type.id()));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("move")
                || args[0].equalsIgnoreCase("resize"))) {
            service.boards().forEach(board -> options.add(board.id()));
        }
        var typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(typed));
        return options;
    }
}
