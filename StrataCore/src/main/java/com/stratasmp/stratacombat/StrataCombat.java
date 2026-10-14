package com.stratasmp.stratacombat;

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;
import com.stratasmp.strataduels.StrataDuels;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

/** Combat tag: hitting or being hit by a player locks teleport commands, and logging out while tagged kills you. */
public class StrataCombat extends StrataModule implements Listener {
    private static final NamespacedKey BOSS_ID = new NamespacedKey("stratabosses", "boss_id");

    private CombatTags tags;
    private final Set<String> blockedCommands = new HashSet<>();
    private final Set<String> exemptWorlds = new HashSet<>();
    private boolean tagOnMobs;
    private boolean tagOnBosses;
    private boolean blockTeleports;
    private boolean killOnQuit;

    public StrataCombat(StrataCore core) {
        super(core, "StrataCombat");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        load();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("combat") != null) getCommand("combat").setExecutor(this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 10L, 10L);
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    private void load() {
        reloadConfig();
        var cfg = getConfig();
        tags = new CombatTags(cfg.getLong("duration-seconds", 15), System::currentTimeMillis);
        tagOnMobs = cfg.getBoolean("tag-on-mobs", false);
        tagOnBosses = cfg.getBoolean("tag-on-bosses", true);
        blockTeleports = cfg.getBoolean("block-teleports", true);
        killOnQuit = cfg.getBoolean("kill-on-quit", true);
        blockedCommands.clear();
        for (String command : cfg.getStringList("blocked-commands")) blockedCommands.add(command.toLowerCase(Locale.ROOT));
        exemptWorlds.clear();
        for (String world : cfg.getStringList("exempt-worlds")) exemptWorlds.add(world.toLowerCase(Locale.ROOT));
    }

    public boolean isTagged(UUID id) {
        return tags.isTagged(id);
    }

    public void untag(UUID id) {
        tags.untag(id);
    }

    /** Duelists play by the duel rules, spectators and creative players can't be fought, and some worlds are exempt. */
    private boolean exempt(Player player) {
        if (player.hasPermission("stratacombat.bypass")) return true;
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return true;
        if (exemptWorlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT))) return true;
        StrataDuels duels = core().module(StrataDuels.class);
        return duels != null && duels.isDueling(player.getUniqueId());
    }

    private void tag(Player player) {
        if (exempt(player)) return;
        if (tags.tag(player.getUniqueId())) {
            player.sendMessage(Component.text("You are in combat! Don't log out.", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || event.getFinalDamage() <= 0) return;
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) source = shooter;
        if (source instanceof Player attacker) {
            if (attacker.equals(victim)) return;
            tag(attacker);
            tag(victim);
        } else if (isTaggingMob(source)) {
            tag(victim);
        }
    }

    private boolean isTaggingMob(Entity source) {
        if (tagOnBosses && source.getPersistentDataContainer().has(BOSS_ID, PersistentDataType.STRING)) return true;
        return tagOnMobs && source instanceof Monster;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        tags.untag(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!tags.isTagged(player.getUniqueId())) return;
        tags.untag(player.getUniqueId());
        if (!killOnQuit || exempt(player)) return;
        player.setHealth(0.0);
        Bukkit.broadcast(Component.text(player.getName() + " logged out in combat.", NamedTextColor.RED));
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (blockedCommands.isEmpty() || !tags.isTagged(player.getUniqueId())) return;
        String message = event.getMessage();
        int space = message.indexOf(' ');
        String root = (space < 0 ? message.substring(1) : message.substring(1, space)).toLowerCase(Locale.ROOT);
        int colon = root.indexOf(':');
        if (colon >= 0) root = root.substring(colon + 1);
        if (!blockedCommands.contains(root)) return;
        event.setCancelled(true);
        player.sendMessage(Component.text("You can't use that in combat (" + tags.secondsLeft(player.getUniqueId()) + "s left).", NamedTextColor.RED));
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!blockTeleports || !tags.isTagged(event.getPlayer().getUniqueId())) return;
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.COMMAND || cause == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("You can't teleport while in combat.", NamedTextColor.RED));
        }
    }

    private void tick() {
        for (UUID id : tags.drainExpired()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.sendActionBar(Component.text("You are no longer in combat.", NamedTextColor.GREEN));
            }
        }
        for (UUID id : tags.tagged()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.sendActionBar(Component.text("In combat: " + tags.secondsLeft(id) + "s", NamedTextColor.RED));
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 && sender instanceof Player player) {
            int left = tags.secondsLeft(player.getUniqueId());
            player.sendMessage(Component.text(left > 0 ? "In combat for " + left + "s." : "You are not in combat.",
                    left > 0 ? NamedTextColor.RED : NamedTextColor.GREEN));
            return true;
        }
        if (!sender.hasPermission("stratacombat.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            load();
            sender.sendMessage(Component.text("StrataCombat reloaded.", NamedTextColor.GREEN));
            return true;
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("untag") || args[0].equalsIgnoreCase("check"))) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return true;
            }
            if (args[0].equalsIgnoreCase("untag")) {
                tags.untag(target.getUniqueId());
                sender.sendMessage(Component.text(target.getName() + " is no longer tagged.", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text(target.getName() + ": " + tags.secondsLeft(target.getUniqueId()) + "s of combat left.", NamedTextColor.YELLOW));
            }
            return true;
        }
        sender.sendMessage(Component.text("/combat [reload|check <player>|untag <player>]", NamedTextColor.GRAY));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("stratacombat.admin")) return List.of();
        if (args.length == 1) return List.of("reload", "check", "untag");
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }
}
