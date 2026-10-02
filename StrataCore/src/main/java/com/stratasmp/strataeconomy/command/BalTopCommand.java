package com.stratasmp.strataeconomy.command;

import com.stratasmp.strataeconomy.StrataEconomy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BalTopCommand implements CommandExecutor {

    private static final int PER_PAGE = 10;

    private final StrataEconomy plugin;

    public BalTopCommand(StrataEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException ignored) {
            }
        }
        final int p = page;
        plugin.async(() -> {
            List<Map.Entry<UUID, Long>> top = plugin.stratas().top(p * PER_PAGE);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.msg().send(sender, "baltop-header", Map.of("page", String.valueOf(p)));
                int start = (p - 1) * PER_PAGE;
                for (int i = start; i < Math.min(top.size(), start + PER_PAGE); i++) {
                    Map.Entry<UUID, Long> e = top.get(i);
                    plugin.msg().send(sender, "baltop-line", Map.of(
                            "rank", String.valueOf(i + 1),
                            "player", plugin.stratas().nameOf(e.getKey()),
                            "balance", plugin.money(e.getValue())));
                }
            });
        });
        return true;
    }
}
