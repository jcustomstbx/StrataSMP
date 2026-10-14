package com.stratasmp.stratabosses;

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;
import java.util.List;


public class StrataBosses extends StrataModule {
   public StrataBosses(StrataCore core) {
      super(core, "StrataBosses");
   }

   private PeriodicBossSpawner spawner;
   private BossManager bossManager;
   private BossRegistry registry;

   public void onEnable() {
      this.saveDefaultConfig();
      BossAbilities.setDamageMultiplier(this.getConfig().getDouble("ability-damage-multiplier", 1.0));
      int leftovers = BossArenaBuilder.restoreLeftovers(this);
      if (leftovers > 0) {
         this.getLogger().info("Restored " + leftovers + " boss arena(s) left standing by the last shutdown.");
      }
      this.registry = new BossRegistry(this);
      BossRegistry registry = this.registry;
      this.bossManager = new BossManager(this, registry);
      this.getServer().getPluginManager().registerEvents(bossManager, this);
      bossManager.patchAlreadyActiveBosses();
      this.spawner = new PeriodicBossSpawner(this, registry, bossManager);
      this.spawner.start();
      SpawnBossCommand spawnBossCommand = new SpawnBossCommand(registry, bossManager);
      this.getCommand("spawnboss").setExecutor(spawnBossCommand);
      this.getCommand("spawnboss").setTabCompleter(spawnBossCommand);
      BossAdminCommand admin = new BossAdminCommand(bossManager);
      this.getCommand("boss").setExecutor(admin);
      this.getCommand("boss").setTabCompleter(admin);
      this.getLogger().info("Loaded " + registry.all().size() + " boss profiles.");
   }

   public void onDisable() {
      if (this.bossManager != null) this.bossManager.shutdown();
      if (this.spawner != null) {
         this.spawner.stop();
      }
   }

   public boolean throwTrident(org.bukkit.entity.LivingEntity boss) {
      return this.bossManager != null && this.bossManager.throwTrident(boss);
   }

   /** Spawns a boss for a scheduled event. A blank or unknown id picks a random enabled boss. Returns the boss's display name, or null if nothing spawned. */
   public String spawnEventBoss(String bossId) {
      if (this.spawner == null || this.registry == null) return null;
      BossDefinition def = bossId == null || bossId.isBlank() ? null : this.registry.byId(bossId);
      List<BossDefinition> order = new java.util.ArrayList<>();
      if (def != null) {
         order.add(def);
      } else {
         for (BossDefinition candidate : this.registry.all().values()) if (candidate.enabled) order.add(candidate);
         java.util.Collections.shuffle(order);
      }
      for (BossDefinition candidate : order) {
         if (this.spawner.spawnEvent(candidate)) return candidate.displayName;
      }
      return null;
   }
}
