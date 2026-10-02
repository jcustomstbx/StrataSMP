package com.stratasmp.stratabosses;

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;


public class StrataBosses extends StrataModule {
   public StrataBosses(StrataCore core) {
      super(core, "StrataBosses");
   }

   private PeriodicBossSpawner spawner;
   private BossManager bossManager;

   public void onEnable() {
      this.saveDefaultConfig();
      BossAbilities.setDamageMultiplier(this.getConfig().getDouble("ability-damage-multiplier", 1.0));
      int leftovers = BossArenaBuilder.restoreLeftovers(this);
      if (leftovers > 0) {
         this.getLogger().info("Restored " + leftovers + " boss arena(s) left standing by the last shutdown.");
      }
      BossRegistry registry = new BossRegistry(this);
      this.bossManager = new BossManager(this, registry);
      this.getServer().getPluginManager().registerEvents(bossManager, this);
      bossManager.patchAlreadyActiveBosses();
      this.spawner = new PeriodicBossSpawner(this, registry, bossManager);
      this.spawner.start();
      SpawnBossCommand spawnBossCommand = new SpawnBossCommand(registry, bossManager);
      this.getCommand("spawnboss").setExecutor(spawnBossCommand);
      this.getCommand("spawnboss").setTabCompleter(spawnBossCommand);
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
}
