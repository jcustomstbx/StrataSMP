package com.stratasmp.stratabosses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.scheduler.BukkitTask;

public class PeriodicBossSpawner {
   private final StrataModule plugin;
   private final BossRegistry registry;
   private final BossManager bossManager;
   private final BossArenaBuilder arenaBuilder;
   private final Set<String> allowedWorlds;
   private final Set<String> excludedWorlds;
   private BukkitTask task;

   public PeriodicBossSpawner(StrataModule plugin, BossRegistry registry, BossManager bossManager) {
      this.plugin = plugin;
      this.registry = registry;
      this.bossManager = bossManager;
      this.arenaBuilder = new BossArenaBuilder();
      List<String> allowed = plugin.getConfig().getStringList("spawn-checks.allowed-worlds");
      this.allowedWorlds = Set.copyOf(allowed.isEmpty() ? List.of("world") : allowed);
      this.excludedWorlds = Set.copyOf(plugin.getConfig().getStringList("spawn-checks.excluded-worlds"));
   }

   public void start() {
      long intervalTicks = Math.max(1L, this.plugin.getConfig().getLong("spawn-checks.interval-minutes", 10L)) * 60L * 20L;
      this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
         // at most one boss per check, and the order is shuffled so no boss is favoured
         List<BossDefinition> order = new ArrayList<>(this.registry.all().values());
         Collections.shuffle(order);
         for (BossDefinition def : order) {
            if (this.trySpawn(def)) {
               break;
            }
         }
      }, intervalTicks, intervalTicks);
   }

   public void stop() {
      if (this.task != null) {
         this.task.cancel();
      }
   }

   private boolean trySpawn(BossDefinition def) {
      if (!def.enabled || this.bossManager.isOnCooldown(def) || ThreadLocalRandom.current().nextDouble() >= def.spawnChance) {
         return false;
      }
      Player target = this.randomEligiblePlayer();
      if (target == null || this.bossManager.activeCountInWorld(def, target.getWorld()) >= def.maxConcurrentPerWorld) {
         return false;
      }
      Location roughSpot = this.randomNearbyLocation(target);
      BossArenaBuilder.ArenaResult arena = this.arenaBuilder.build(roughSpot, def.id);
      LivingEntity entity = (LivingEntity)target.getWorld().spawnEntity(arena.spawnLocation, def.baseType);
      if (!entity.isValid()) {
         BossArenaBuilder.restore(arena.snapshot);
         return false;
      }
      this.bossManager.makeBoss(entity, def);
      this.bossManager.attachArena(entity.getUniqueId(), arena.snapshot);
      Bukkit.broadcast(Component.text(def.displayName + " has appeared somewhere in " + target.getWorld().getName() + "...", NamedTextColor.GOLD));
      return true;
   }

   private Player randomEligiblePlayer() {
      List<Player> candidates = new ArrayList<>();

      for (Player player : Bukkit.getOnlinePlayers()) {
         String worldName = player.getWorld().getName();
         if (player.getWorld().getEnvironment() == Environment.NORMAL && this.allowedWorlds.contains(worldName) && !this.excludedWorlds.contains(worldName)) {
            candidates.add(player);
         }
      }

      return candidates.isEmpty() ? null : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
   }

   private Location randomNearbyLocation(Player player) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      double angle = random.nextDouble(0.0, Math.PI * 2);
      double distance = random.nextDouble(20.0, 40.0);
      double x = player.getLocation().getX() + Math.cos(angle) * distance;
      double z = player.getLocation().getZ() + Math.sin(angle) * distance;
      World world = player.getWorld();
      int y = world.getHighestBlockYAt((int)Math.floor(x), (int)Math.floor(z));
      return new Location(world, x, y + 1, z);
   }
}
