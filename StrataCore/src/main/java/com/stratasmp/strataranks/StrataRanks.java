package com.stratasmp.strataranks;

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/** Cosmetic rank choices plus the restricted /name and /repair perks. */
public final class StrataRanks extends StrataModule implements Listener {
    private File choicesFile;
    private YamlConfiguration choices;
    private RankPlaceholders placeholders;
    private boolean chatColorCleanupBroken;
    private final java.util.Set<UUID> appliedNameColor = new java.util.HashSet<>();

    public StrataRanks(StrataCore core) { super(core, "StrataRanks"); }

    @Override public void onEnable() {
        saveDefaultConfig();
        choicesFile = new File(getDataFolder(), "choices.yml");
        choices = YamlConfiguration.loadConfiguration(choicesFile);
        getServer().getPluginManager().registerEvents(this, this);
        for (String name : List.of("rankstyle", "name", "repair")) {
            if (getCommand(name) != null) getCommand(name).setExecutor(this);
        }
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            var existing = me.clip.placeholderapi.PlaceholderAPIPlugin.getInstance()
                    .getLocalExpansionManager().getExpansion("strataranks");
            if (existing != null && existing.getClass().getName().equals(RankPlaceholders.class.getName())) {
                existing.unregister();
            }
            placeholders = new RankPlaceholders(this);
            if (!placeholders.register()) getLogger().warning("Could not register strataranks placeholders.");
        }
        getServer().getScheduler().runTaskTimer(this, this::particles, 10L, 10L);
        getServer().getScheduler().runTaskTimer(this, this::removeExpiredCosmetics, 20L, 400L);
    }

    @Override public void onDisable() {
        if (placeholders != null) placeholders.unregister();
        super.onDisable();
    }

    String nameColor(Player player) {
        if (RankTier.of(player) == RankTier.NONE) return "";
        return choices.getString(player.getUniqueId() + ".name-color", "");
    }

    private void saveChoices() {
        try { choices.save(choicesFile); }
        catch (IOException error) { getLogger().severe("Could not save rank choices: " + error.getMessage()); }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        RankTier tier = RankTier.of(player);
        if (tier == RankTier.NONE) { player.sendMessage("This perk requires Imperial, Ornate or Regal."); return true; }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "rankstyle" -> style(player, tier, args);
            case "name" -> nameItem(player, tier, args);
            case "repair" -> repair(player, tier, args);
            default -> { return false; }
        }
        return true;
    }

    private void style(Player player, RankTier tier, String[] args) {
        if (args.length < 2) {
            player.sendMessage("/rankstyle name <#RRGGBB|reset>, chat <imperial-gold|royal-aqua|regal-rose>, particle <crown|aurora|ember|off>, join <message|reset>");
            return;
        }
        UUID id = player.getUniqueId();
        String choice = args[1].toLowerCase(Locale.ROOT);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "name" -> {
                if (choice.equals("reset")) {
                    choices.set(id + ".name-color", null);
                    player.sendMessage("Name colour reset.");
                } else {
                    String hex = args[1].startsWith("#") ? args[1] : "#" + args[1];
                    if (!hex.matches("#[0-9a-fA-F]{6}")) { player.sendMessage("Use a colour such as #A020F0."); return; }
                    choices.set(id + ".name-color", hex.toUpperCase(Locale.ROOT));
                    player.sendMessage("Name colour set to " + hex + ".");
                }
                refreshName(player);
            }
            case "chat" -> {
                if (!List.of("imperial-gold", "royal-aqua", "regal-rose").contains(choice)) {
                    player.sendMessage("Choose imperial-gold, royal-aqua or regal-rose."); return;
                }
                Plugin chatColor = getServer().getPluginManager().getPlugin("ChatColor");
                if (chatColor == null || !chatColor.isEnabled()) { player.sendMessage("Chat colours are unavailable right now."); return; }
                try {
                    Object api = chatColor.getClass().getMethod("getChatColorAPI").invoke(chatColor);
                    api.getClass().getMethod("setColor", Player.class, String.class).invoke(api, player, choice);
                    player.sendMessage("Chat colour set to " + choice + ".");
                } catch (ReflectiveOperationException | RuntimeException error) {
                    getLogger().warning("ChatColor integration failed: " + error);
                    player.sendMessage("Could not change your chat colour right now.");
                }
            }
            case "particle" -> {
                List<String> allowed = tier == RankTier.REGAL ? List.of("crown", "aurora", "ember", "off") : List.of("crown", "off");
                if (!allowed.contains(choice)) { player.sendMessage("Available particles: " + String.join(", ", allowed)); return; }
                choices.set(id + ".particle", choice);
                saveChoices();
                player.sendMessage("Particle set to " + choice + ".");
            }
            case "join" -> {
                if (tier != RankTier.REGAL) { player.sendMessage("Custom join messages require Regal."); return; }
                String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
                if (message.equalsIgnoreCase("reset")) { choices.set(id + ".join", null); saveChoices(); player.sendMessage("Join message reset."); return; }
                if (message.length() > 60 || message.length() < 3 || message.contains("§") || message.contains("&") || message.contains("<") || message.contains(">") || message.contains("\n")) {
                    player.sendMessage("Use 3-60 plain characters without formatting codes."); return;
                }
                choices.set(id + ".join", message);
                saveChoices();
                player.sendMessage("Join message saved.");
            }
            default -> { player.sendMessage("Use /rankstyle name, chat, particle or join."); return; }
        }
        if (args[0].equalsIgnoreCase("name")) saveChoices();
    }

    private void nameItem(Player player, RankTier tier, String[] args) {
        if (tier != RankTier.REGAL) { player.sendMessage("/name requires Regal."); return; }
        if (!isSmpWorld(player)) { player.sendMessage("/name is available in the SMP worlds."); return; }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) { player.sendMessage("Hold the item you want to name."); return; }
        if (args.length == 0) { player.sendMessage("/name <plain item name|reset>"); return; }
        String value = String.join(" ", args).trim();
        if (value.length() > 40 || value.contains("§") || value.contains("&") || value.contains("<") || value.contains(">")) {
            player.sendMessage("Use at most 40 plain characters without formatting codes."); return;
        }
        ItemMeta meta = hand.getItemMeta();
        meta.displayName(value.equalsIgnoreCase("reset") ? null : Component.text(value));
        hand.setItemMeta(meta);
        player.sendMessage("Item name updated.");
    }

    private void repair(Player player, RankTier tier, String[] args) {
        if (tier != RankTier.REGAL) { player.sendMessage("/repair requires Regal."); return; }
        if (!isSmpWorld(player)) { player.sendMessage("/repair is available in the SMP worlds."); return; }
        if (args.length != 0) { player.sendMessage("/repair repairs the item in your hand."); return; }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || !(hand.getItemMeta() instanceof Damageable damageable) || hand.getType().getMaxDurability() <= 0) {
            player.sendMessage("Hold a damaged tool, weapon or armour piece."); return;
        }
        if (damageable.getDamage() <= 0) { player.sendMessage("That item is already fully repaired."); return; }
        long now = System.currentTimeMillis();
        long next = choices.getLong(player.getUniqueId() + ".repair-at", 0L);
        if (now < next) { player.sendMessage("/repair is ready in " + Math.max(1, (next - now + 999) / 1000) + " seconds."); return; }
        damageable.setDamage(0);
        hand.setItemMeta(damageable);
        choices.set(player.getUniqueId() + ".repair-at", now + getConfig().getLong("repair-cooldown-seconds", 86400L) * 1000L);
        saveChoices();
        player.sendMessage("Item repaired.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        refreshName(player);
        if (RankTier.of(player) == RankTier.NONE) removeExclusiveChatColor(player);
        if (event.joinMessage() != null && RankTier.of(player) == RankTier.REGAL) {
            String message = choices.getString(player.getUniqueId() + ".join", "");
            if (!message.isBlank()) event.joinMessage(Component.text("[+] " + player.getName() + " " + message));
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        appliedNameColor.remove(event.getPlayer().getUniqueId());
    }

    private void refreshName(Player player) {
        String hex = nameColor(player);
        if (RankTier.of(player) == RankTier.NONE) return;
        TextColor color = hex.isEmpty() ? net.kyori.adventure.text.format.NamedTextColor.WHITE : TextColor.fromHexString(hex);
        String visible = org.bukkit.ChatColor.stripColor(player.getDisplayName());
        if (visible == null || visible.isBlank()) visible = player.getName();
        player.displayName(Component.text(visible, color));
        player.playerListName(Component.text(visible, color));
        if (hex.isEmpty()) appliedNameColor.remove(player.getUniqueId());
        else appliedNameColor.add(player.getUniqueId());
    }

    private void particles() {
        for (Player player : getServer().getOnlinePlayers()) {
            if (!isSmpWorld(player) && !player.getWorld().getName().equals("hub")) continue;
            RankTier tier = RankTier.of(player);
            if (tier == RankTier.NONE) continue;
            String choice = choices.getString(player.getUniqueId() + ".particle", "crown");
            if (tier != RankTier.REGAL && !choice.equals("crown")) continue;
            var at = player.getLocation().add(0, 2.15, 0);
            switch (choice) {
                case "crown" -> player.getWorld().spawnParticle(Particle.DUST, at, 2, 0.25, 0.02, 0.25, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 210, 45), 1.2f));
                case "aurora" -> player.getWorld().spawnParticle(Particle.DUST, at, 2, 0.35, 0.1, 0.35, 0,
                        new Particle.DustOptions(Color.fromRGB(85, 235, 240), 1.2f));
                case "ember" -> player.getWorld().spawnParticle(Particle.FLAME, at, 2, 0.25, 0.1, 0.25, 0.01);
                default -> { }
            }
        }
    }

    private void removeExpiredCosmetics() {
        for (Player player : getServer().getOnlinePlayers()) {
            if (RankTier.of(player) != RankTier.NONE) continue;
            removeExclusiveChatColor(player);
            if (appliedNameColor.remove(player.getUniqueId())) {
                String visible = org.bukkit.ChatColor.stripColor(player.getDisplayName());
                if (visible == null || visible.isBlank()) visible = player.getName();
                player.displayName(Component.text(visible, net.kyori.adventure.text.format.NamedTextColor.WHITE));
                player.playerListName(Component.text(visible, net.kyori.adventure.text.format.NamedTextColor.WHITE));
            }
        }
    }

    private void removeExclusiveChatColor(Player player) {
        if (chatColorCleanupBroken) return;
        Plugin chatColor = getServer().getPluginManager().getPlugin("ChatColor");
        if (chatColor == null || !chatColor.isEnabled()) return;
        try {
            Object api = chatColor.getClass().getMethod("getChatColorAPI").invoke(chatColor);
            Object data = api.getClass().getMethod("getPlayerData", UUID.class).invoke(api, player.getUniqueId());
            if (data == null) return;
            String key = String.valueOf(data.getClass().getMethod("getColorKey").invoke(data));
            if (List.of("imperial-gold", "royal-aqua", "regal-rose").contains(key)) {
                api.getClass().getMethod("resetColor", Player.class).invoke(api, player);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            chatColorCleanupBroken = true;
            getLogger().warning("Could not revoke an expired exclusive chat colour: " + error);
        }
    }

    private static boolean isSmpWorld(Player player) {
        return List.of("world", "world_nether", "world_the_end").contains(player.getWorld().getName());
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("rankstyle") || args.length == 0) return List.of();
        if (args.length == 1) return List.of("name", "chat", "particle", "join");
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "name" -> List.of("#A020F0", "reset");
            case "chat" -> List.of("imperial-gold", "royal-aqua", "regal-rose");
            case "particle" -> List.of("crown", "aurora", "ember", "off");
            case "join" -> List.of("reset");
            default -> List.of();
        };
    }
}
