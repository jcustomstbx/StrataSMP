package com.stratasmp.stratahub;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;

public final class HubCommand implements CommandExecutor {

    private final StrataHub plugin;

    public HubCommand(StrataHub plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /stratahub <addentry|removeentry|addcomingsoon|removecomingsoon|list|reload|lockdown|dressnpc>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "addentry" -> {
                NPC npc = selectedNpc(sender);
                if (npc != null) {
                    plugin.hubData().addEntry(npc.getId());
                    plugin.msg().send(sender, "entry-added", "%id%", String.valueOf(npc.getId()));
                }
            }
            case "removeentry" -> {
                NPC npc = selectedNpc(sender);
                if (npc != null) {
                    plugin.hubData().removeEntry(npc.getId());
                    plugin.msg().send(sender, "entry-removed", "%id%", String.valueOf(npc.getId()));
                }
            }
            case "addcomingsoon" -> {
                NPC npc = selectedNpc(sender);
                if (npc != null) {
                    plugin.hubData().addComingSoon(npc.getId());
                    plugin.msg().send(sender, "comingsoon-added", "%id%", String.valueOf(npc.getId()));
                }
            }
            case "removecomingsoon" -> {
                NPC npc = selectedNpc(sender);
                if (npc != null) {
                    plugin.hubData().removeComingSoon(npc.getId());
                    plugin.msg().send(sender, "comingsoon-removed", "%id%", String.valueOf(npc.getId()));
                }
            }
            case "list" -> listEntries(sender);
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage("StrataHub config reloaded.");
            }
            case "lockdown" -> lockdown(sender, args);
            case "dressnpc" -> dressNpc(sender, args);
            default -> sender.sendMessage("Usage: /stratahub <addentry|removeentry|addcomingsoon|removecomingsoon|list|reload|lockdown|dressnpc>");
        }
        return true;
    }

    private void dressNpc(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /stratahub dressnpc <npcId> <rank>");
            return;
        }
        NPC npc;
        try {
            npc = CitizensAPI.getNPCRegistry().getById(Integer.parseInt(args[1]));
        } catch (NumberFormatException e) {
            sender.sendMessage("NPC id must be a number.");
            return;
        }
        if (npc == null) {
            sender.sendMessage("No NPC with id " + args[1] + ".");
            return;
        }
        Map<EquipmentSlot, ItemStack> gear = plugin.npcGear().forRank(args[2]);
        if (gear.isEmpty()) {
            sender.sendMessage("No kit called '" + args[2] + "' in StrataKits (is StrataKits installed?).");
            return;
        }
        Equipment equipment = npc.getOrAddTrait(Equipment.class);
        gear.forEach(equipment::set);
        sender.sendMessage("Dressed NPC #" + npc.getId() + " in the " + args[2].toLowerCase() + " kit (" + gear.size() + " pieces).");
    }

    private void lockdown(CommandSender sender, String[] args) {
        if (args.length < 2) {
            boolean current = plugin.hubData().isSmpLockdown();
            sender.sendMessage("SMP lockdown is currently " + (current ? "ON" : "OFF") + ". Usage: /stratahub lockdown <on|off>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "on" -> {
                plugin.hubData().setSmpLockdown(true);
                sender.sendMessage("SMP lockdown enabled - non-bypassing players can no longer enter the SMP worlds.");
            }
            case "off" -> {
                plugin.hubData().setSmpLockdown(false);
                sender.sendMessage("SMP lockdown disabled.");
            }
            default -> sender.sendMessage("Usage: /stratahub lockdown <on|off>");
        }
    }

    private void listEntries(CommandSender sender) {
        Set<Integer> ids = plugin.hubData().entryNpcIds();
        plugin.msg().send(sender, ids.isEmpty() ? "entry-list-empty" : "entry-list-header");
        for (int id : ids) {
            NPC npc = CitizensAPI.getNPCRegistry().getById(id);
            sender.sendMessage(" - #" + id + " " + (npc != null ? npc.getName() : "(NPC no longer exists)"));
        }

        Set<Integer> comingSoon = plugin.hubData().comingSoonNpcIds();
        if (!comingSoon.isEmpty()) {
            sender.sendMessage("Coming-soon NPCs:");
            for (int id : comingSoon) {
                NPC npc = CitizensAPI.getNPCRegistry().getById(id);
                sender.sendMessage(" - #" + id + " " + (npc != null ? npc.getName() : "(NPC no longer exists)"));
            }
        }
    }

    private NPC selectedNpc(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return null;
        }
        NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(player);
        if (npc == null) {
            plugin.msg().send(player, "no-npc-selected");
        }
        return npc;
    }
}
