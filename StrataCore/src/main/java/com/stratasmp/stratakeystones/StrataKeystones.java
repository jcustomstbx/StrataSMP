package com.stratasmp.stratakeystones;

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;
import com.stratasmp.stratateams.StrataTeams;
import com.stratasmp.stratateams.Team;
import com.stratasmp.stratateams.TeamManager;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
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
import org.bukkit.Material;
import org.bukkit.Tag;
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
    private final NamespacedKey runMob = KeystoneMobs.KEY;
    private File pendingFile;
    private final Map<UUID, List<Integer>> pendingReturns = new HashMap<>();
    private final Map<UUID, KeystoneRun> runs = new HashMap<>(); // every participant -> their run
    private final Set<KeystoneRun> active = new LinkedHashSet<>();
    private TeamManager teams;
    private final Map<UUID, KeystoneRun> invites = new HashMap<>(); // invited teammate -> run they may join
    private final Map<EntityType, Integer> mobPool = new java.util.LinkedHashMap<>();
    private List<Integer> waveSizes;

    public StrataKeystones(StrataCore core) {
        super(core, "StrataKeystones");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        loadPending();
        StrataTeams teamModule = core().module(StrataTeams.class);
        if (teamModule != null) teams = teamModule.getTeamManager();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("keystone") != null) getCommand("keystone").setExecutor(this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 10L, 10L);
    }

    @Override
    public void onDisable() {
        for (KeystoneRun run : new ArrayList<>(active)) end(run, false, "The server is restarting.");
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
            UUID owner = KeystoneMobs.owner(entity);
            // the opener may have dropped out of the run, so find it by owner rather than by participant
            for (KeystoneRun open : active) {
                if (open.owner.equals(owner)) open.alive.remove(entity.getUniqueId());
            }
            return;
        }
        Player killer = entity.getKiller();
        if (killer == null || !(entity instanceof Monster)) return;
        if (!worldAllowed(entity.getWorld(), "drops.worlds")) return;
        // only mobs that spawned the ordinary way count; spawners, eggs, slime splits, raids, traps and the like are farmable
        var reason = entity.getEntitySpawnReason();
        if (reason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL
                && reason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT
                && reason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CHUNK_GEN) return;
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
        if (!worldAllowed(player.getWorld(), "run.worlds")) {
            player.sendMessage(Component.text("Keystones can't be opened in this world.", NamedTextColor.RED));
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        start(player, Math.min(level, maxLevel()));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        drop(event.getEntity().getUniqueId(), "You died.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        drop(event.getPlayer().getUniqueId(), "You left the server.");
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        drop(event.getPlayer().getUniqueId(), "You left the keystone area.");
    }

    /** Overworld-type worlds only, and when the config lists worlds the world must be one of them. */
    private boolean worldAllowed(World world, String path) {
        if (world.getEnvironment() != World.Environment.NORMAL) return false;
        List<String> allowed = getConfig().getStringList(path);
        return allowed.isEmpty() || allowed.stream().anyMatch(w -> w.equalsIgnoreCase(world.getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRunMobHurt(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!KeystoneMobs.isRunMob(event.getEntity())) return;
        Entity damager = event.getDamager();
        if (damager instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (!(damager instanceof Player player)) return;
        KeystoneRun run = runs.get(player.getUniqueId());
        if (run != null) run.contributors.add(player.getUniqueId());
    }

    /** Run mobs (creepers especially) never break blocks. */
    @EventHandler(ignoreCancelled = true)
    public void onRunMobExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        if (KeystoneMobs.isRunMob(event.getEntity())) event.blockList().clear();
    }

    /** Reinforcements, jockeys, splits and conversions born next to a run mob are run mobs too. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onOffspring(org.bukkit.event.entity.CreatureSpawnEvent event) {
        var reason = event.getSpawnReason();
        if (reason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
                && reason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.JOCKEY
                && reason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SLIME_SPLIT
                && reason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.INFECTION
                && reason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DROWNED
                && reason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.MOUNT) return;
        if (runs.isEmpty()) return;
        for (LivingEntity near : event.getLocation().getNearbyLivingEntities(8.0, KeystoneMobs::isRunMob)) {
            UUID owner = KeystoneMobs.owner(near);
            if (owner != null) {
                KeystoneMobs.tag(event.getEntity(), owner);
                event.getEntity().setPersistent(false);
            }
            return;
        }
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        deliverPending(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        getServer().getScheduler().runTask(this, () -> deliverPending(player));
    }

    private void loadPending() {
        pendingFile = new File(getDataFolder(), "pending.yml");
        pendingReturns.clear();
        if (!pendingFile.exists()) return;
        var yaml = com.stratasmp.stratacore.AtomicYaml.load(pendingFile, getLogger());
        for (String key : yaml.getKeys(false)) {
            try {
                pendingReturns.put(UUID.fromString(key), new ArrayList<>(yaml.getIntegerList(key)));
            } catch (IllegalArgumentException ignored) {
                // a stray key in the file
            }
        }
    }

    private void savePending() {
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        for (Map.Entry<UUID, List<Integer>> e : pendingReturns.entrySet()) yaml.set(e.getKey().toString(), e.getValue());
        try {
            getDataFolder().mkdirs();
            com.stratasmp.stratacore.AtomicYaml.save(yaml, pendingFile);
        } catch (java.io.IOException e) {
            getLogger().warning("Could not save pending keystones: " + e.getMessage());
        }
    }

    /** Gives a keystone to its owner now, or holds it until they are online and alive. */
    private void handBack(UUID ownerId, int level) {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline() && !owner.isDead()) {
            give(owner, KeystoneItem.create(level));
            return;
        }
        pendingReturns.computeIfAbsent(ownerId, k -> new ArrayList<>()).add(level);
        savePending();
    }

    private void deliverPending(Player player) {
        List<Integer> levels = pendingReturns.get(player.getUniqueId());
        if (levels == null || player.isDead()) return;
        // the keystone of a run still in progress stays held until that run ends
        for (KeystoneRun open : active) if (open.owner.equals(player.getUniqueId())) return;
        pendingReturns.remove(player.getUniqueId());
        savePending();
        for (int level : levels) give(player, KeystoneItem.create(level));
        player.sendMessage(Component.text("Your keystone has been returned.", NamedTextColor.LIGHT_PURPLE));
    }

    private void start(Player player, int level) {
        long deadline = System.currentTimeMillis() + getConfig().getLong("run.time-limit-seconds", 300) * 1000L;
        KeystoneRun run = new KeystoneRun(player.getUniqueId(), level, player.getLocation().clone(), deadline);
        // the keystone is held in pending.yml until the run ends; a crash mid-run then hands it back on next login
        run.reserved = level;
        pendingReturns.computeIfAbsent(run.owner, k -> new ArrayList<>()).add(level);
        savePending();
        active.add(run);
        runs.put(run.owner, run);
        player.showTitle(Title.title(Component.text("Keystone Lv. " + level, NamedTextColor.LIGHT_PURPLE),
                Component.text("Prepare yourself", NamedTextColor.GRAY)));
        Set<UUID> invited = partyAround(player);
        for (UUID id : invited) {
            Player member = Bukkit.getPlayer(id);
            if (member == null) continue;
            invites.put(id, run);
            member.sendMessage(Component.text(player.getName() + " opened a level " + level + " keystone. ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("[Click to join]", NamedTextColor.GREEN, net.kyori.adventure.text.format.TextDecoration.BOLD)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/keystone join"))
                            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("Join the run")))));
        }
        long window = invited.isEmpty() ? 3000L : getConfig().getLong("party.join-window-seconds", 10) * 1000L;
        run.nextWaveAt = System.currentTimeMillis() + window;
    }

    /** /keystone join: a teammate accepts an invite while the run is still waiting for its first wave. */
    private void join(Player player) {
        KeystoneRun run = invites.get(player.getUniqueId());
        if (run == null || !active.contains(run) || run.wave > 0) {
            invites.remove(player.getUniqueId());
            player.sendMessage(Component.text("There is no keystone run you can join right now.", NamedTextColor.RED));
            return;
        }
        double radius = getConfig().getDouble("party.join-radius", 30) * 2;
        if (runs.containsKey(player.getUniqueId()) || !player.getWorld().equals(run.origin.getWorld())
                || player.getLocation().distanceSquared(run.origin) > radius * radius) {
            player.sendMessage(Component.text("You are too far from the keystone to join.", NamedTextColor.RED));
            return;
        }
        if (run.players.size() >= getConfig().getInt("party.max-size", 5)) {
            player.sendMessage(Component.text("That run is full.", NamedTextColor.RED));
            return;
        }
        invites.remove(player.getUniqueId());
        run.players.add(player.getUniqueId());
        runs.put(player.getUniqueId(), run);
        player.showTitle(Title.title(Component.text("Keystone Lv. " + run.level, NamedTextColor.LIGHT_PURPLE),
                Component.text("Fight alongside your team", NamedTextColor.GRAY)));
        Player owner = Bukkit.getPlayer(run.owner);
        if (owner != null) owner.sendMessage(Component.text(player.getName() + " joined your keystone run.", NamedTextColor.GREEN));
    }

    /** Teammates close enough to the opener who are free to be invited. */
    private Set<UUID> partyAround(Player opener) {
        Set<UUID> party = new LinkedHashSet<>();
        if (teams == null || !getConfig().getBoolean("party.enabled", true)) return party;
        Team team = teams.getTeam(opener.getUniqueId());
        if (team == null) return party;
        double radius = getConfig().getDouble("party.join-radius", 30);
        int max = getConfig().getInt("party.max-size", 5);
        for (UUID id : team.members) {
            if (party.size() + 1 >= max) break;
            Player member = Bukkit.getPlayer(id);
            if (member == null || member.equals(opener) || runs.containsKey(id)) continue;
            if (!member.getWorld().equals(opener.getWorld()) || member.isDead()) continue;
            if (member.getLocation().distanceSquared(opener.getLocation()) <= radius * radius) party.add(id);
        }
        return party;
    }

    /** A participant died, left or strayed. The run only ends once nobody is left in it. */
    private void drop(UUID id, String reason) {
        KeystoneRun run = runs.remove(id);
        if (run == null) return;
        run.players.remove(id);
        Player player = Bukkit.getPlayer(id);
        if (player != null && player.isOnline()) {
            player.sendMessage(Component.text("You are out of the keystone run: " + reason, NamedTextColor.RED));
        }
        if (run.players.isEmpty()) end(run, false, null);
    }

    private void tick() {
        if (active.isEmpty()) return;
        long now = System.currentTimeMillis();
        double leash = getConfig().getDouble("run.leash-radius", 40);
        for (KeystoneRun run : new ArrayList<>(active)) {
            if (now > run.deadline) { end(run, false, "Time ran out."); continue; }
            Player lead = null;
            for (UUID id : new ArrayList<>(run.players)) {
                Player member = Bukkit.getPlayer(id);
                if (member == null || !member.isOnline()) { drop(id, "You left the server."); continue; }
                if (!member.getWorld().equals(run.origin.getWorld())
                        || member.getLocation().distanceSquared(run.origin) > leash * leash) {
                    drop(id, "You strayed too far from the keystone.");
                    continue;
                }
                if (lead == null) lead = member;
            }
            if (!active.contains(run) || lead == null) continue;
            run.alive.removeIf(id -> {
                Entity e = Bukkit.getEntity(id);
                if (e != null) {
                    run.missingSince.remove(id);
                    return !e.isValid() || e.isDead();
                }
                // not found: killed and removed normally (the death handler already took it out) or in an unloaded
                // chunk; give it a few seconds before treating it as gone so a stray lookup can't skip a wave
                long since = run.missingSince.computeIfAbsent(id, k -> now);
                return now - since > 10000L;
            });
            if (!run.alive.isEmpty()) continue;
            if (run.nextWaveAt < 0) {
                run.nextWaveAt = now + getConfig().getLong("run.wave-delay-ticks", 60) * 50L;
                continue;
            }
            if (now < run.nextWaveAt) continue;
            if (run.wave >= 3) { end(run, true, null); continue; }
            spawnWave(run, lead);
        }
    }

    private void spawnWave(KeystoneRun run, Player player) {
        run.wave++;
        double scale = (1 + getConfig().getDouble("run.size-scale-per-level", 0.25) * (run.level - 1))
                * (1 + getConfig().getDouble("party.size-scale-per-extra-member", 0.5) * (run.players.size() - 1));
        int count = (int) Math.round(waveSizes.get(run.wave - 1) * scale);
        List<EntityType> eligible = new ArrayList<>();
        for (Map.Entry<EntityType, Integer> entry : mobPool.entrySet()) {
            if (entry.getValue() <= run.level) eligible.add(entry.getKey());
        }
        if (eligible.isEmpty()) eligible.add(EntityType.ZOMBIE);
        double health = 1 + getConfig().getDouble("run.health-scale-per-level", 0.15) * (run.level - 1);
        double damage = 1 + getConfig().getDouble("run.damage-scale-per-level", 0.10) * (run.level - 1);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Location at = findSpawn(run.origin, player);
            EntityType type = eligible.get(rng.nextInt(eligible.size()));
            Entity spawned;
            try {
                spawned = at.getWorld().spawnEntity(at, type);
            } catch (RuntimeException e) {
                continue;
            }
            // a region mob-deny, mob cap or peaceful difficulty leaves an invalid entity behind
            if (!(spawned instanceof Mob mob) || !spawned.isValid()) { spawned.remove(); continue; }
            KeystoneMobs.tag(mob, run.owner);
            mob.setPersistent(false);
            mob.setRemoveWhenFarAway(false);
            mob.setCanPickupItems(false);
            if (mob instanceof Zombie z) z.setShouldBurnInDay(false);
            if (mob instanceof AbstractSkeleton s) s.setShouldBurnInDay(false);
            scaleAttribute(mob, Attribute.MAX_HEALTH, health);
            mob.setHealth(mob.getAttribute(Attribute.MAX_HEALTH).getValue());
            scaleAttribute(mob, Attribute.ATTACK_DAMAGE, damage);
            Player target = Bukkit.getPlayer(new ArrayList<>(run.players).get(rng.nextInt(run.players.size())));
            mob.setTarget(target != null ? target : player);
            run.alive.add(mob.getUniqueId());
        }
        if (run.alive.isEmpty()) {
            // nothing could be spawned: this must fail, never count as a cleared wave
            end(run, false, "Mobs could not spawn here. Try another spot.");
            return;
        }
        run.nextWaveAt = -1;
        for (UUID id : run.players) {
            Player member = Bukkit.getPlayer(id);
            if (member != null) {
                member.showTitle(Title.title(Component.text("Wave " + run.wave + "/3", NamedTextColor.GOLD),
                        Component.text(run.alive.size() + " mobs", NamedTextColor.GRAY)));
            }
        }
    }

    private void scaleAttribute(Mob mob, Attribute attribute, double multiplier) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(instance.getBaseValue() * multiplier);
    }

    /** Picks a standable, dry spot in a ring around the player, falling back to the player's own position. */
    private Location findSpawn(Location origin, Player player) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double min = Math.max(2.0, getConfig().getDouble("run.spawn-radius-min", 6));
        double max = Math.max(min + 1.0, getConfig().getDouble("run.spawn-radius-max", 11));
        World world = player.getWorld();
        Location base = player.getLocation();
        int[] offsets = {0, -1, 1, -2, 2, -3, 3};
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = rng.nextDouble(Math.PI * 2);
            double radius = rng.nextDouble(min, max);
            int x = (int) Math.floor(base.getX() + Math.cos(angle) * radius);
            int z = (int) Math.floor(base.getZ() + Math.sin(angle) * radius);
            // closest to the player's height first, so mobs land beside them rather than on roofs or canopies
            for (int dy : offsets) {
                int y = base.getBlockY() + dy;
                var floor = world.getBlockAt(x, y - 1, z);
                var feet = world.getBlockAt(x, y, z);
                var head = world.getBlockAt(x, y + 1, z);
                if (floor.isSolid() && !Tag.LEAVES.isTagged(floor.getType())
                        && feet.isPassable() && !feet.isLiquid() && feet.getType() != Material.FIRE
                        && head.isPassable() && !head.isLiquid()) {
                    return new Location(world, x + 0.5, y, z + 0.5);
                }
            }
        }
        return base;
    }

    private void end(KeystoneRun run, boolean success, String reason) {
        active.remove(run);
        invites.values().removeIf(r -> r == run);
        releaseReservation(run);
        for (UUID id : run.alive) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        run.alive.clear();
        List<UUID> members = new ArrayList<>(run.players);
        for (UUID id : members) runs.remove(id);
        run.players.clear();
        if (success) {
            int next = Math.min(run.level + 1, maxLevel());
            handBack(run.owner, next);
            for (UUID id : members) {
                Player member = Bukkit.getPlayer(id);
                if (member == null) continue;
                member.sendMessage(Component.text("Keystone cleared! Level " + next + " unlocked.", NamedTextColor.GREEN));
                // rewards go to the opener and to teammates who actually fought, not to anyone standing nearby
                if (!id.equals(run.owner) && !run.contributors.contains(id)) {
                    member.sendMessage(Component.text("You didn't take part in the fight, so you get no reward.", NamedTextColor.GRAY));
                    continue;
                }
                for (String command : getConfig().getStringList("rewards." + run.level)) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            command.replace("%player%", member.getName()).replace("%level%", String.valueOf(run.level)));
                }
            }
        } else {
            if (reason != null) {
                for (UUID id : members) {
                    Player member = Bukkit.getPlayer(id);
                    if (member != null) member.sendMessage(Component.text("Keystone failed: " + reason, NamedTextColor.RED));
                }
            }
            if (getConfig().getBoolean("run.return-on-fail", true)) handBack(run.owner, run.level);
        }
    }

    /** The run is over, so the keystone held back at the start is no longer pending (the result is handed back below). */
    private void releaseReservation(KeystoneRun run) {
        if (run.reserved <= 0) return;
        List<Integer> held = pendingReturns.get(run.owner);
        if (held != null) {
            held.remove(Integer.valueOf(run.reserved));
            if (held.isEmpty()) pendingReturns.remove(run.owner);
            savePending();
        }
        run.reserved = 0;
    }

    private void give(Player player, ItemStack item) {
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("join")) {
            if (sender instanceof Player player) join(player);
            else sender.sendMessage(Component.text("Only players can join a run.", NamedTextColor.RED));
            return true;
        }
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
        sender.sendMessage(Component.text("/keystone <join|give <player> [level]|stop <player>|reload>", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return sender.hasPermission("stratakeystones.admin") ? List.of("join", "give", "stop", "reload") : List.of("join");
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
