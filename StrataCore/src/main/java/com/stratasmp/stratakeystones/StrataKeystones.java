package com.stratasmp.stratakeystones;

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Overworld keystone drops and the 3-wave runs they unlock. */
public final class StrataKeystones extends StrataModule implements Listener {
    private final NamespacedKey runMob = new NamespacedKey("stratasmp", "keystone_mob");
    private final Map<UUID, KeystoneRun> runs = new HashMap<>();
    private final Map<EntityType, Integer> mobPool = new java.util.LinkedHashMap<>();
    private List<Integer> waveSizes;

    public StrataKeystones(StrataCore core) {
        super(core, "StrataKeystones");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("keystone") != null) getCommand("keystone").setExecutor(this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 10L, 10L);
    }

    @Override
    public void onDisable() {
        for (KeystoneRun run : new ArrayList<>(runs.values())) end(run, false, "The server is restarting.");
        super.onDisable();
    }

    private void loadSettings() {
        reloadConfig();
        waveSizes = getConfig().getIntegerList("run.wave-sizes");
        if (waveSizes.size() != 3) waveSizes = List.of(4, 6, 9);
        mobPool.clear();
        var section = getConfig().getConfigurationSection("run.mobs");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    mobPool.put(EntityType.valueOf(key.toUpperCase(Locale.ROOT)), section.getInt(key, 1));
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Unknown keystone mob type: " + key);
                }
            }
        }
        if (mobPool.isEmpty()) mobPool.put(EntityType.ZOMBIE, 1);
    }

    private int maxLevel() { return getConfig().getInt("run.max-level", 10); }

    @EventHandler(ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getPersistentDataContainer().has(runMob, PersistentDataType.BYTE)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }
        Player killer = entity.getKiller();
        if (killer == null || !(entity instanceof Monster)) return;
        if (entity.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        if (ThreadLocalRandom.current().nextDouble() >= getConfig().getDouble("drops.chance", 0.02)) return;
        event.getDrops().add(KeystoneItem.create(1));
        killer.sendMessage(Component.text("A Keystone dropped!", NamedTextColor.LIGHT_PURPLE));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        int level = KeystoneItem.level(hand);
        if (level <= 0) return;
        event.setCancelled(true);
        if (runs.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("You already have a keystone run active.", NamedTextColor.RED));
            return;
        }
        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) {
            player.sendMessage(Component.text("Keystones can only be opened in the overworld.", NamedTextColor.RED));
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        start(player, Math.min(level, maxLevel()));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        KeystoneRun run = runs.get(event.getEntity().getUniqueId());
        if (run != null) end(run, false, "You died.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        KeystoneRun run = runs.get(event.getPlayer().getUniqueId());
        if (run != null) end(run, false, "You left the server.");
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        KeystoneRun run = runs.get(event.getPlayer().getUniqueId());
        if (run != null) end(run, false, "You left the keystone area.");
    }

    private void start(Player player, int level) {
        long deadline = System.currentTimeMillis() + getConfig().getLong("run.time-limit-seconds", 300) * 1000L;
        KeystoneRun run = new KeystoneRun(player.getUniqueId(), level, player.getLocation().clone(), deadline);
        runs.put(player.getUniqueId(), run);
        player.showTitle(Title.title(Component.text("Keystone Lv. " + level, NamedTextColor.LIGHT_PURPLE),
                Component.text("Prepare yourself", NamedTextColor.GRAY)));
        run.nextWaveAt = System.currentTimeMillis() + 3000L;
    }

    private void tick() {
        if (runs.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (KeystoneRun run : new ArrayList<>(runs.values())) {
            Player player = Bukkit.getPlayer(run.player);
            if (player == null || !player.isOnline()) { end(run, false, null); continue; }
            if (now > run.deadline) { end(run, false, "Time ran out."); continue; }
            double leash = getConfig().getDouble("run.leash-radius", 40);
            if (!player.getWorld().equals(run.origin.getWorld())
                    || player.getLocation().distanceSquared(run.origin) > leash * leash) {
                end(run, false, "You strayed too far from the keystone.");
                continue;
            }
            run.alive.removeIf(id -> {
                Entity e = Bukkit.getEntity(id);
                return e == null || !e.isValid() || e.isDead();
            });
            if (!run.alive.isEmpty()) continue;
            if (run.nextWaveAt < 0) {
                run.nextWaveAt = now + getConfig().getLong("run.wave-delay-ticks", 60) * 50L;
                continue;
            }
            if (now < run.nextWaveAt) continue;
            if (run.wave >= 3) { end(run, true, null); continue; }
            spawnWave(run, player);
        }
    }

    private void spawnWave(KeystoneRun run, Player player) {
        run.wave++;
        double scale = 1 + getConfig().getDouble("run.size-scale-per-level", 0.25) * (run.level - 1);
        int count = (int) Math.round(waveSizes.get(run.wave - 1) * scale);
        List<EntityType> eligible = new ArrayList<>();
        for (Map.Entry<EntityType, Integer> entry : mobPool.entrySet()) {
            if (entry.getValue() <= run.level) eligible.add(entry.getKey());
        }
        double health = 1 + getConfig().getDouble("run.health-scale-per-level", 0.15) * (run.level - 1);
        double damage = 1 + getConfig().getDouble("run.damage-scale-per-level", 0.10) * (run.level - 1);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Location at = findSpawn(run.origin, player);
            EntityType type = eligible.get(rng.nextInt(eligible.size()));
            Entity spawned = at.getWorld().spawnEntity(at, type);
            if (!(spawned instanceof Mob mob)) { spawned.remove(); continue; }
            mob.getPersistentDataContainer().set(runMob, PersistentDataType.BYTE, (byte) 1);
            mob.setPersistent(false);
            mob.setRemoveWhenFarAway(false);
            mob.setCanPickupItems(false);
            if (mob instanceof Zombie z) z.setShouldBurnInDay(false);
            if (mob instanceof AbstractSkeleton s) s.setShouldBurnInDay(false);
            scaleAttribute(mob, Attribute.MAX_HEALTH, health);
            mob.setHealth(mob.getAttribute(Attribute.MAX_HEALTH).getValue());
            scaleAttribute(mob, Attribute.ATTACK_DAMAGE, damage);
            mob.setTarget(player);
            run.alive.add(mob.getUniqueId());
        }
        run.nextWaveAt = -1;
        player.showTitle(Title.title(Component.text("Wave " + run.wave + "/3", NamedTextColor.GOLD),
                Component.text(count + " mobs", NamedTextColor.GRAY)));
    }

    private void scaleAttribute(Mob mob, Attribute attribute, double multiplier) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(instance.getBaseValue() * multiplier);
    }

    /** Picks a standable spot in a ring around the player, falling back to the player's own position. */
    private Location findSpawn(Location origin, Player player) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double min = getConfig().getDouble("run.spawn-radius-min", 6);
        double max = getConfig().getDouble("run.spawn-radius-max", 11);
        World world = player.getWorld();
        Location base = player.getLocation();
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = rng.nextDouble(Math.PI * 2);
            double radius = rng.nextDouble(min, max);
            int x = (int) Math.floor(base.getX() + Math.cos(angle) * radius);
            int z = (int) Math.floor(base.getZ() + Math.sin(angle) * radius);
            for (int dy = 3; dy >= -3; dy--) {
                int y = base.getBlockY() + dy;
                if (world.getBlockAt(x, y - 1, z).isSolid()
                        && world.getBlockAt(x, y, z).isPassable()
                        && world.getBlockAt(x, y + 1, z).isPassable()) {
                    return new Location(world, x + 0.5, y, z + 0.5);
                }
            }
        }
        return base;
    }

    private void end(KeystoneRun run, boolean success, String reason) {
        runs.remove(run.player);
        for (UUID id : run.alive) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        run.alive.clear();
        Player player = Bukkit.getPlayer(run.player);
        if (success) {
            int next = Math.min(run.level + 1, maxLevel());
            if (player != null) {
                player.sendMessage(Component.text("Keystone cleared! Reached level " + next + ".", NamedTextColor.GREEN));
                give(player, KeystoneItem.create(next));
                for (String command : getConfig().getStringList("rewards." + run.level)) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            command.replace("%player%", player.getName()).replace("%level%", String.valueOf(run.level)));
                }
            }
        } else {
            if (player != null && reason != null) {
                player.sendMessage(Component.text("Keystone failed: " + reason, NamedTextColor.RED));
            }
            if (player != null && getConfig().getBoolean("run.return-on-fail", true)) {
                give(player, KeystoneItem.create(run.level));
            }
        }
    }

    private void give(Player player, ItemStack item) {
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("stratakeystones.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            sender.sendMessage(Component.text("Keystone config reloaded.", NamedTextColor.GREEN));
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) { sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)); return true; }
            int level = 1;
            if (args.length >= 3) {
                try { level = Math.max(1, Math.min(maxLevel(), Integer.parseInt(args[2]))); }
                catch (NumberFormatException e) { sender.sendMessage(Component.text("Level must be a number.", NamedTextColor.RED)); return true; }
            }
            give(target, KeystoneItem.create(level));
            sender.sendMessage(Component.text("Gave " + target.getName() + " a level " + level + " keystone.", NamedTextColor.GREEN));
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("stop")) {
            Player target = Bukkit.getPlayer(args[1]);
            KeystoneRun run = target == null ? null : runs.get(target.getUniqueId());
            if (run == null) { sender.sendMessage(Component.text("No active run.", NamedTextColor.RED)); return true; }
            end(run, false, "Stopped by an admin.");
            return true;
        }
        sender.sendMessage(Component.text("/keystone <give <player> [level]|stop <player>|reload>", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("give", "stop", "reload");
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
