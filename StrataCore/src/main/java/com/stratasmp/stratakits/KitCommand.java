package com.stratasmp.stratakits;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** /kit opens your kit; /kit preview <rank>; staff get /kit reset <player> [rank] and /kit reload. */
final class KitCommand implements CommandExecutor, TabCompleter {

    private final KitService service;
    private final KitGui gui;
    private final RankShowcase showcase;
    private final Runnable reload;

    KitCommand(KitService service, KitGui gui, RankShowcase showcase, Runnable reload) {
        this.service = service;
        this.gui = gui;
        this.showcase = showcase;
        this.reload = reload;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (command.getName().equalsIgnoreCase("rankpreview")) {
            return rankPreview(sender, args);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("stratakits.admin")) {
                return denied(sender);
            }
            reload.run();
            sender.sendMessage(Component.text("Kits reloaded.", NamedTextColor.GREEN));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reset")) {
            return reset(sender, args);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        Kit target;
        if (args.length >= 2 && args[0].equalsIgnoreCase("preview")) {
            target = service.catalog().get(args[1]);
            if (target == null) {
                player.sendMessage(Component.text("No kit called " + args[1] + ".", NamedTextColor.RED));
                return true;
            }
        } else {
            target = service.catalog().kitFor(player);
            if (target == null) {
                player.sendMessage(Component.text("Kits come with a rank - see /store. Previewing the entry kit.", NamedTextColor.YELLOW));
                List<Kit> ladder = service.catalog().ascending();
                if (ladder.isEmpty()) {
                    return true;
                }
                target = ladder.get(0);
            }
        }
        gui.open(player, target);
        return true;
    }

    /** /rankpreview <rank> opens the rank menu the hub NPCs show; it has its own name because the hub's command guard blocks /kit. */
    private boolean rankPreview(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        Kit kit = args.length >= 1 ? service.catalog().get(args[0]) : service.catalog().kitFor(player);
        if (kit == null) {
            player.sendMessage(Component.text(args.length >= 1 ? "No rank called " + args[0] + "." : "Usage: /rankpreview <rank>", NamedTextColor.RED));
            return true;
        }
        showcase.open(player, kit);
        return true;
    }

    private boolean reset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("stratakits.admin")) {
            return denied(sender);
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /kit reset <player> [rank]", NamedTextColor.RED));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(Component.text("No player found: " + args[1], NamedTextColor.RED));
            return true;
        }
        String kit = args.length >= 3 ? args[2].toLowerCase() : null;
        service.claims().reset(target.getUniqueId(), kit);
        sender.sendMessage(Component.text("Reset " + (kit == null ? "all kit cooldowns" : "the " + kit + " kit cooldown")
                + " for " + target.getName() + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean denied(CommandSender sender) {
        sender.sendMessage(Component.text("You can't do that.", NamedTextColor.RED));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("preview");
            if (sender.hasPermission("stratakits.admin")) {
                out.add("reset");
                out.add("reload");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("preview")
                || args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            service.catalog().ascending().forEach(k -> out.add(k.id()));
        }
        String typed = args[args.length - 1].toLowerCase();
        out.removeIf(s -> !s.startsWith(typed));
        return out;
    }
}