package com.stratasmp.stratabosses;

import com.stratasmp.strataeconomy.api.StrataApi;
import com.stratasmp.stratammo.StrataMMO;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import java.io.File;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.scheduler.BukkitTask;

public class BossManager implements Listener {
   private final StrataModule plugin;
   private final BossRegistry registry;
   private final ModelEngineBridge models;
   private final BossTridents tridents;
   private final BossMelee melee;
   private final BukkitTask modelTask;
   private final NamespacedKey bossIdKey;
   private final NamespacedKey colorTellMarkerKey;
   private final Map<UUID, BossManager.ActiveBoss> active = new HashMap<>();
   private final Map<String, Long> lastDeathAtMillis = new HashMap<>();
   private static final double ENRAGE_HEALTH_FRACTION = 0.5;
   private static final double ENRAGE_DAMAGE_MULTIPLIER = 1.4;
   private static final double ENRAGE_SPEED_MULTIPLIER = 1.25;
   private static final long REACTIVE_PROC_COOLDOWN_MILLIS = 4000L;
   private final Set<UUID> enragedBosses = new HashSet<>();
   private final Map<UUID, Long> lastReactiveProcAtMillis = new HashMap<>();
   private static final long ARENA_REVERT_DELAY_TICKS = 160L;
   private final Map<UUID, BossArenaBuilder.ArenaSnapshot> arenaSnapshots = new HashMap<>();
   private final Map<UUID, BukkitTask> arenaExpiry = new HashMap<>();
   private final NamespacedKey projectileMarker;
   private final File cooldownFile;
   private final Map<UUID, Map<UUID, Double>> damageDealt = new HashMap<>();
   private double weaponDropChance;
   private final List<LootRoll> supplyLoot = new ArrayList<>();
   private long idleDespawnMillis;
   private final Map<UUID, Long> lastCombatAtMillis = new HashMap<>();

   public BossManager(StrataModule plugin, BossRegistry registry) {
      this.plugin = plugin;
      this.registry = registry;
      this.models = new ModelEngineBridge(plugin.getLogger(),
         plugin.getConfig().getBoolean("models.enabled", true)
         && plugin.getServer().getPluginManager().isPluginEnabled("ModelEngine"));
      this.modelTask = Bukkit.getScheduler().runTaskTimer(plugin, this.models::tick, 1L, 4L);
      this.bossIdKey = new NamespacedKey(plugin, "boss_id");
      this.colorTellMarkerKey = new NamespacedKey(plugin, "color_tell_marker");
      this.melee = new BossMelee(plugin, this.models, this.bossIdKey);
      plugin.getServer().getPluginManager().registerEvents(this.melee, plugin);
      this.tridents = new BossTridents(plugin, this.models, this.bossIdKey);
      plugin.getServer().getPluginManager().registerEvents(this.tridents, plugin);
      this.projectileMarker = new NamespacedKey(plugin, "boss-projectile");
      this.cooldownFile = new File(plugin.getDataFolder(), "cooldowns.yml");
      this.loadCooldowns();
      this.loadLoot();
   }

   /** Re-reads loot, idle timer and per-boss stats from the config (spawn schedule and worlds still need a restart). */
   public void reload() {
      this.plugin.reloadConfig();
      BossAbilities.setDamageMultiplier(this.plugin.getConfig().getDouble("ability-damage-multiplier", 1.0));
      this.registry.reload(this.plugin);
      this.loadLoot();
   }

   private void loadLoot() {
      this.idleDespawnMillis = Math.max(0L, this.plugin.getConfig().getLong("spawn-checks.idle-despawn-minutes", 15L)) * 60000L;
      this.supplyLoot.clear();
      ConfigurationSection lootSection = this.plugin.getConfig().getConfigurationSection("loot");
      this.weaponDropChance = lootSection != null ? lootSection.getDouble("weapon-drop-chance", 0.08) : 0.08;
      ConfigurationSection suppliesSection = lootSection != null ? lootSection.getConfigurationSection("supplies") : null;
      if (suppliesSection != null) {
         for (String key : suppliesSection.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
               this.plugin.getLogger().warning("Unknown material in loot.supplies: " + key);
            } else {
               this.supplyLoot.add(LootRoll.fromConfig(suppliesSection.getConfigurationSection(key), material));
            }
         }
      }
   }

   private void loadCooldowns() {
      if (!this.cooldownFile.exists()) return;
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.cooldownFile);
      for (String id : yaml.getKeys(false)) this.lastDeathAtMillis.put(id, yaml.getLong(id));
   }

   private void saveCooldowns() {
      YamlConfiguration yaml = new YamlConfiguration();
      for (Map.Entry<String, Long> e : this.lastDeathAtMillis.entrySet()) yaml.set(e.getKey(), e.getValue());
      Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
         try {
            this.plugin.getDataFolder().mkdirs();
            yaml.save(this.cooldownFile);
         } catch (java.io.IOException e) {
            this.plugin.getLogger().warning("Couldn't save boss cooldowns: " + e.getMessage());
         }
      });
   }

   private void announce(String message, NamedTextColor color) {
      Bukkit.broadcast(Component.text(message, color));
   }

   public void shutdown() {
      this.modelTask.cancel();
      this.melee.close();
      this.tridents.close();
      this.models.close();
      for (ActiveBoss boss : this.active.values()) {
         boss.bar().removeAll();
         boss.barTask().cancel();
         boss.abilityTask().cancel();
      }
      this.active.clear();
      // a stopping server (or a plugin reload) must not leave arenas standing
      for (BukkitTask expiry : this.arenaExpiry.values()) {
         expiry.cancel();
      }
      this.arenaExpiry.clear();
      for (BossArenaBuilder.ArenaSnapshot snapshot : this.arenaSnapshots.values()) {
         BossArenaBuilder.restore(snapshot);
      }
      this.arenaSnapshots.clear();
   }

   public boolean throwTrident(LivingEntity boss) {
      return this.tridents.throwAtNearest(boss);
   }

   private void attachModel(LivingEntity entity, BossDefinition def) {
      boolean modeled = this.models.attach(entity, def.modelId, def.weaponModelId);
      boolean hasMarker = false;
      for (Entity passenger : List.copyOf(entity.getPassengers())) {
         if (passenger.getPersistentDataContainer().has(this.colorTellMarkerKey, PersistentDataType.BYTE)) {
            if (modeled) passenger.remove();
            else hasMarker = true;
         }
      }
      if (!modeled && !def.wearsArmor && !hasMarker) this.spawnColorTellPassenger(entity, def);
   }

   public void attachArena(UUID bossUuid, BossArenaBuilder.ArenaSnapshot snapshot) {
      if (snapshot == null) {
         return;
      }
      this.arenaSnapshots.put(bossUuid, snapshot);
      snapshot.persist(this.plugin, bossUuid);
      // the arena comes down after this long even if the fight is still going
      long lifetimeTicks = Math.max(1L, this.plugin.getConfig().getLong("arena.max-lifetime-minutes", 20L)) * 60L * 20L;
      this.arenaExpiry.put(bossUuid, Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         this.arenaExpiry.remove(bossUuid);
         BossArenaBuilder.restore(this.arenaSnapshots.remove(bossUuid));
      }, lifetimeTicks));
   }

   /** Takes the boss's arena snapshot out of play (stopping its expiry timer) so the caller can restore it. */
   private BossArenaBuilder.ArenaSnapshot releaseArena(UUID bossUuid) {
      BukkitTask expiry = this.arenaExpiry.remove(bossUuid);
      if (expiry != null) {
         expiry.cancel();
      }
      return this.arenaSnapshots.remove(bossUuid);
   }

   public boolean isOnCooldown(BossDefinition def) {
      Long lastDeath = this.lastDeathAtMillis.get(def.id);
      return lastDeath == null ? false : System.currentTimeMillis() - lastDeath < def.respawnCooldownSeconds * 1000L;
   }

   public int activeCountInWorld(BossDefinition def, World world) {
      int count = 0;

      for (BossManager.ActiveBoss activeBoss : this.active.values()) {
         if (activeBoss.def().id.equals(def.id) && activeBoss.world().equals(world)) {
            count++;
         }
      }

      return count;
   }

   @EventHandler
   public void onEntitiesLoad(org.bukkit.event.world.EntitiesLoadEvent event) {
      for (Entity entity : event.getEntities()) {
         if (entity instanceof LivingEntity living) this.adoptBoss(living);
      }
   }

   private void adoptBoss(LivingEntity entity) {
      String id = entity.getPersistentDataContainer().get(this.bossIdKey, PersistentDataType.STRING);
      BossDefinition def = id == null ? null : this.registry.byId(id);
      if (def != null && !this.active.containsKey(entity.getUniqueId())) {
         this.protectBossForm(entity, def.id);
         this.attachModel(entity, def);
         this.registerActive(entity, def);
      }
   }

   public void patchAlreadyActiveBosses() {
      for (World world : Bukkit.getWorlds()) {
         for (LivingEntity entity : world.getLivingEntities()) {
            String bossId = (String)entity.getPersistentDataContainer().get(this.bossIdKey, PersistentDataType.STRING);
            if (bossId != null) {
               entity.setCanPickupItems(false);
               BossDefinition def = this.registry.byId(bossId);
               if (def != null) {
                  this.protectBossForm(entity, def.id);
                  AttributeInstance speedAttr = entity.getAttribute(Attribute.MOVEMENT_SPEED);
                  if (speedAttr != null) {
                     speedAttr.setBaseValue(def.movementSpeed);
                  }

                  AttributeInstance followAttr = entity.getAttribute(Attribute.FOLLOW_RANGE);
                  if (followAttr != null) {
                     followAttr.setBaseValue(def.followRange);
                  }

                  // A boss that outlived a restart has no boss bar, no ability timer and
                  // isn't tracked for idle despawn - re-adopt it so it behaves like a
                  // freshly spawned one from here on.
                  if (!this.active.containsKey(entity.getUniqueId())) {
                     this.attachModel(entity, def);
                     this.registerActive(entity, def);
                  }
               }
            }
         }

         for (ArmorStand entityx : world.getEntitiesByClass(ArmorStand.class)) {
            boolean isMarked = entityx.getPersistentDataContainer().has(this.colorTellMarkerKey, PersistentDataType.BYTE);
            if (isMarked && entityx.getVehicle() == null) {
               entityx.remove();
            }
         }
      }
   }

   private void protectBossForm(LivingEntity entity, String bossId) {
      // Overworld bosses must keep the entity that owns their model, health and tasks.
      // Piglin brutes otherwise become a new zombified piglin after 300 ticks.
      if (entity instanceof org.bukkit.entity.PiglinAbstract piglin) {
         piglin.setImmuneToZombification(true);
      }
      if ("abyssal_coilfang".equals(bossId) && entity instanceof org.bukkit.entity.Zombie zombie) {
         zombie.setShouldBurnInDay(false);
         // Paper 1.21.11's Mob.burnUndead uses the entity-type tag directly and
         // bypasses Zombie.shouldBurnInDay. Permanent headgear blocks that native
         // sunlight path without cancelling fire from combat or the environment.
         EntityEquipment equipment = entity.getEquipment();
         if (equipment != null) {
            ItemStack helmet = equipment.getHelmet();
            boolean upgradingSunProtection = helmet == null || helmet.getType().isAir() || !helmet.getItemMeta().isUnbreakable();
            if (helmet == null || helmet.getType().isAir()) {
               BossDefinition def = this.registry.byId(bossId);
               helmet = this.leatherPiece(Material.LEATHER_HELMET, def);
            }
            ItemMeta meta = helmet.getItemMeta();
            meta.setUnbreakable(true);
            if (meta instanceof org.bukkit.inventory.meta.Damageable damaged) damaged.setDamage(0);
            helmet.setItemMeta(meta);
            equipment.setHelmet(helmet);
            equipment.setHelmetDropChance(0F);
            // Extinguish legacy sunburn once when migrating the old, breakable helmet.
            // Later reloads preserve fires from normal combat and environmental sources.
            if (upgradingSunProtection) entity.setFireTicks(0);
         }
      }
   }

   public void makeBoss(LivingEntity entity, BossDefinition def) {
      this.protectBossForm(entity, def.id);
      entity.setCustomName(def.displayName);
      entity.setCustomNameVisible(true);
      entity.setRemoveWhenFarAway(false);
      entity.setPersistent(true);
      entity.setCanPickupItems(false);
      entity.getPersistentDataContainer().set(this.bossIdKey, PersistentDataType.STRING, def.id);
      AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
      if (maxHealthAttr != null) {
         double health = def.maxHealth * this.partyHealthScale(entity);
         maxHealthAttr.setBaseValue(health);
         entity.setHealth(health);
      }

      AttributeInstance damageAttr = entity.getAttribute(Attribute.ATTACK_DAMAGE);
      if (damageAttr != null) {
         damageAttr.setBaseValue(def.attackDamage);
      }

      AttributeInstance kbAttr = entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
      if (kbAttr != null) {
         kbAttr.setBaseValue(def.knockbackResistance);
      }

      AttributeInstance speedAttr = entity.getAttribute(Attribute.MOVEMENT_SPEED);
      if (speedAttr != null) {
         speedAttr.setBaseValue(def.movementSpeed);
      }

      AttributeInstance followAttr = entity.getAttribute(Attribute.FOLLOW_RANGE);
      if (followAttr != null) {
         followAttr.setBaseValue(def.followRange);
      }

      EntityEquipment equipment = entity.getEquipment();
      if (equipment != null) {
         if (def.wearsArmor) {
            equipment.setHelmet(this.leatherPiece(Material.LEATHER_HELMET, def));
            equipment.setChestplate(this.leatherPiece(Material.LEATHER_CHESTPLATE, def));
            equipment.setLeggings(this.leatherPiece(Material.LEATHER_LEGGINGS, def));
            equipment.setBoots(this.leatherPiece(Material.LEATHER_BOOTS, def));
            equipment.setHelmetDropChance(0.0F);
            equipment.setChestplateDropChance(0.0F);
            equipment.setLeggingsDropChance(0.0F);
            equipment.setBootsDropChance(0.0F);
         }

         equipment.setItemInMainHand(this.weaponItem(def));
         equipment.setItemInMainHandDropChance(0.0F);
      }

      this.attachModel(entity, def);

      this.registerActive(entity, def);
   }

   /** More players near the spawn means a tougher boss: +health-per-extra-player for each, up to max-extra-players. */
   private double partyHealthScale(LivingEntity boss) {
      double perExtra = this.plugin.getConfig().getDouble("scaling.health-per-extra-player", 0.4);
      int maxExtra = this.plugin.getConfig().getInt("scaling.max-extra-players", 4);
      double radius = this.plugin.getConfig().getDouble("scaling.radius", 64.0);
      int nearby = 0;
      for (Player player : boss.getWorld().getPlayers()) {
         GameMode mode = player.getGameMode();
         if ((mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
               && player.getLocation().distanceSquared(boss.getLocation()) <= radius * radius) {
            nearby++;
         }
      }
      return 1.0 + perExtra * Math.min(maxExtra, Math.max(0, nearby - 1));
   }

   private ItemStack leatherPiece(Material material, BossDefinition def) {
      ItemStack piece = new ItemStack(material);
      LeatherArmorMeta meta = (LeatherArmorMeta)piece.getItemMeta();
      meta.setColor(def.armorColor);
      if (material == Material.LEATHER_HELMET && "abyssal_coilfang".equals(def.id)) meta.setUnbreakable(true);
      piece.setItemMeta(meta);
      return piece;
   }

   private ItemStack weaponItem(BossDefinition def) {
      ItemStack weapon = new ItemStack(def.weaponMaterial);
      ItemMeta meta = weapon.getItemMeta();
      meta.setCustomModelData(def.weaponModelData);
      meta.setDisplayName(def.weaponDisplayName);
      weapon.setItemMeta(meta);
      return weapon;
   }

   private void rollLoot(List<ItemStack> drops, BossDefinition def) {
      if (ThreadLocalRandom.current().nextDouble() < this.weaponDropChance) {
         drops.add(this.weaponItem(def));
      }

      for (LootRoll roll : this.supplyLoot) {
         this.addRoll(drops, roll);
      }

      for (LootRoll roll : def.bonusLoot) {
         this.addRoll(drops, roll);
      }
   }

   private void addRoll(List<ItemStack> drops, LootRoll roll) {
      int amount = roll.rollAmount();
      if (amount > 0) {
         drops.add(new ItemStack(roll.material(), amount));
      }
   }

   private void spawnColorTellPassenger(LivingEntity boss, BossDefinition def) {
      ArmorStand stand = (ArmorStand)boss.getWorld().spawn(boss.getLocation(), ArmorStand.class, s -> {
         s.setSmall(true);
         s.setMarker(false);
         s.setInvisible(true);
         s.setInvulnerable(true);
         s.setBasePlate(false);
         s.setPersistent(true);
      });
      stand.getPersistentDataContainer().set(this.colorTellMarkerKey, PersistentDataType.BYTE, (byte)1);
      stand.getEquipment().setChestplate(this.leatherPiece(Material.LEATHER_CHESTPLATE, def));
      boss.addPassenger(stand);
   }

   private void registerActive(LivingEntity boss, BossDefinition def) {
      this.lastCombatAtMillis.put(boss.getUniqueId(), System.currentTimeMillis());
      BossBar bar = Bukkit.createBossBar(def.displayName, def.bossBarColor, BarStyle.SEGMENTED_10, new BarFlag[0]);
      bar.setProgress(1.0);
      BukkitTask barTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
         if (!boss.isValid()) {
            this.cleanupOrphaned(boss.getUniqueId());
         } else if (!boss.isDead()) {
            if (this.idleDespawnMillis > 0L) {
               long lastCombat = this.lastCombatAtMillis.getOrDefault(boss.getUniqueId(), System.currentTimeMillis());
               if (System.currentTimeMillis() - lastCombat >= this.idleDespawnMillis) {
                  this.despawnIdle(boss, def);
                  return;
               }
            }

            AttributeInstance maxHealthAttr = boss.getAttribute(Attribute.MAX_HEALTH);
            double max = maxHealthAttr != null ? maxHealthAttr.getValue() : def.maxHealth;
            bar.setProgress(Math.max(0.0, Math.min(1.0, boss.getHealth() / max)));

            for (Player player : boss.getWorld().getPlayers()) {
               boolean inRange = player.getLocation().distanceSquared(boss.getLocation()) <= 3600.0;
               if (inRange) {
                  bar.addPlayer(player);
               } else {
                  bar.removePlayer(player);
               }
            }
         }
      }, 0L, 20L);
      long abilityTicks = Math.max(20L, def.abilityCooldownSeconds * 20L);
      BukkitTask abilityTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
         if (boss.isValid() && !boss.isDead()) {
            BossAbilities.executeRotation(this.plugin, def.id, boss);
         }
      }, abilityTicks, abilityTicks);
      this.active.put(boss.getUniqueId(), new BossManager.ActiveBoss(def, boss.getWorld(), bar, barTask, abilityTask));
   }

   private void cleanupOrphaned(UUID bossUuid) {
      this.melee.clear(bossUuid);
      this.tridents.clear(bossUuid);
      this.models.detach(bossUuid);
      BossManager.ActiveBoss activeBoss = this.active.remove(bossUuid);
      if (activeBoss != null) {
         activeBoss.bar().removeAll();
         activeBoss.barTask().cancel();
         activeBoss.abilityTask().cancel();
         this.enragedBosses.remove(bossUuid);
         this.lastReactiveProcAtMillis.remove(bossUuid);
         this.lastCombatAtMillis.remove(bossUuid);
         this.damageDealt.remove(bossUuid);
         BossAbilities.clearState(bossUuid);
         BossArenaBuilder.restore(this.releaseArena(bossUuid));
      }
   }

   private void despawnIdle(LivingEntity boss, BossDefinition def) {
      UUID id = boss.getUniqueId();
      this.melee.clear(id);
      this.tridents.clear(id);
      this.models.detach(id);
      boss.getWorld().spawnParticle(Particle.LARGE_SMOKE, boss.getLocation().add(0.0, 1.0, 0.0), 40, 0.5, 0.8, 0.5, 0.02);
      boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0F, 0.6F);

      for (Entity passenger : List.copyOf(boss.getPassengers())) {
         passenger.remove();
      }

      BossManager.ActiveBoss activeBoss = this.active.remove(id);
      if (activeBoss != null) {
         activeBoss.bar().removeAll();
         activeBoss.barTask().cancel();
         activeBoss.abilityTask().cancel();
      }

      this.enragedBosses.remove(id);
      this.lastReactiveProcAtMillis.remove(id);
      this.lastCombatAtMillis.remove(id);
      this.damageDealt.remove(id);
      BossAbilities.clearState(id);
      BossArenaBuilder.restore(this.releaseArena(id));
      boss.remove();
      this.announce(def.displayName + " loses interest and melts back into the world.", NamedTextColor.GRAY);
   }

   private void markCombat(UUID bossUuid) {
      if (bossUuid != null && this.active.containsKey(bossUuid)) {
         this.lastCombatAtMillis.put(bossUuid, System.currentTimeMillis());
      }
   }

   private UUID bossUuidOf(Entity entity) {
      if (entity instanceof LivingEntity living
         && living.getPersistentDataContainer().has(this.bossIdKey, PersistentDataType.STRING)) {
         return living.getUniqueId();
      }

      return null;
   }

   private UUID bossUuidBehind(Entity damager) {
      UUID direct = this.bossUuidOf(damager);
      if (direct != null) {
         return direct;
      }

      return damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
         ? this.bossUuidOf(shooter)
         : null;
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onBossCombat(EntityDamageByEntityEvent event) {
      this.markCombat(this.bossUuidOf(event.getEntity()));
      this.markCombat(this.bossUuidBehind(event.getDamager()));
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onBossDamaged(EntityDamageByEntityEvent event) {
      if (event.getEntity() instanceof LivingEntity boss) {
         String bossId = (String)boss.getPersistentDataContainer().get(this.bossIdKey, PersistentDataType.STRING);
         if (bossId != null && !boss.isDead()) {
            BossDefinition def = this.registry.byId(bossId);
            if (def != null) {
               Player attacker = this.resolvePlayerDamager(event.getDamager());
               if (attacker != null) {
                  this.damageDealt.computeIfAbsent(boss.getUniqueId(), k -> new HashMap<>())
                     .merge(attacker.getUniqueId(), event.getFinalDamage(), Double::sum);
                  this.maybeEnrage(boss, def);
                  this.maybeTriggerReactive(boss, def);
               }
            }
         }
      }
   }

   private Player resolvePlayerDamager(Entity damager) {
      if (damager instanceof Player player) {
         return player;
      } else {
         return damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player ? player : null;
      }
   }

   private void maybeEnrage(LivingEntity boss, BossDefinition def) {
      UUID id = boss.getUniqueId();
      if (!this.enragedBosses.contains(id)) {
         AttributeInstance maxHealthAttr = boss.getAttribute(Attribute.MAX_HEALTH);
         double max = maxHealthAttr != null ? maxHealthAttr.getValue() : def.maxHealth;
         if (!(boss.getHealth() / max > 0.5)) {
            this.enragedBosses.add(id);
            this.models.enrage(boss);
            AttributeInstance damageAttr = boss.getAttribute(Attribute.ATTACK_DAMAGE);
            if (damageAttr != null) {
               damageAttr.setBaseValue(def.attackDamage * 1.4);
            }

            AttributeInstance speedAttr = boss.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speedAttr != null) {
               speedAttr.setBaseValue(def.movementSpeed * 1.25);
            }

            boss.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, boss.getLocation().add(0.0, 1.5, 0.0), 30, 0.6, 0.6, 0.6);
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2F, 1.4F);
            this.announce(def.displayName + " is enraged!", NamedTextColor.RED);
         }
      }
   }

   private void maybeTriggerReactive(LivingEntity boss, BossDefinition def) {
      UUID id = boss.getUniqueId();
      long now = System.currentTimeMillis();
      Long last = this.lastReactiveProcAtMillis.get(id);
      if (last == null || now - last >= 4000L) {
         this.lastReactiveProcAtMillis.put(id, now);
         BossAbilities.executeReactive(this.plugin, def.id, boss);
      }
   }

   @EventHandler
   public void onBossKnockback(EntityKnockbackEvent event) {
      if (event.getEntity() instanceof LivingEntity bossId) {
         String bossIdx = (String)bossId.getPersistentDataContainer().get(this.bossIdKey, PersistentDataType.STRING);
         if (bossIdx != null) {
            BossDefinition def = this.registry.byId(bossIdx);
            if (def != null) {
               event.setKnockback(event.getKnockback().multiply(def.knockbackTakenMultiplier));
            }
         }
      }
   }

   @EventHandler
   public void onProjectileExplode(EntityExplodeEvent event) {
      if (event.getEntity().getPersistentDataContainer().has(this.projectileMarker, PersistentDataType.BYTE)) {
         event.blockList().clear();
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onItemPickup(EntityPickupItemEvent event) {
      // only the bosses are barred from picking things up; every other mob keeps vanilla behaviour
      if (event.getEntity().getPersistentDataContainer().has(this.bossIdKey, PersistentDataType.STRING)) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onDeath(EntityDeathEvent event) {
      LivingEntity entity = event.getEntity();
      String bossId = (String)entity.getPersistentDataContainer().get(this.bossIdKey, PersistentDataType.STRING);
      BossDefinition def = bossId != null ? this.registry.byId(bossId) : null;
      if (def == null) {
         BossManager.ActiveBoss fallback = this.active.get(entity.getUniqueId());
         if (fallback == null) {
            return;
         }

         def = fallback.def();
      }

      this.melee.clear(entity.getUniqueId());
      this.tridents.clear(entity.getUniqueId());
      this.models.detach(entity.getUniqueId());
      this.lastCombatAtMillis.remove(entity.getUniqueId());
      BossManager.ActiveBoss activeBoss = this.active.remove(entity.getUniqueId());
      if (activeBoss != null) {
         activeBoss.bar().removeAll();
         activeBoss.barTask().cancel();
         activeBoss.abilityTask().cancel();
      }

      this.enragedBosses.remove(entity.getUniqueId());
      this.lastReactiveProcAtMillis.remove(entity.getUniqueId());
      BossAbilities.clearState(entity.getUniqueId());
      this.lastDeathAtMillis.put(def.id, System.currentTimeMillis());
      BossArenaBuilder.ArenaSnapshot arenaSnapshot = this.releaseArena(entity.getUniqueId());
      if (arenaSnapshot != null) {
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> BossArenaBuilder.restore(arenaSnapshot), 160L);
      }

      for (Entity passenger : List.copyOf(entity.getPassengers())) {
         passenger.remove();
      }

      event.getDrops().clear();
      event.setDroppedExp(0);
      this.saveCooldowns();
      Map<UUID, Double> damage = this.damageDealt.remove(entity.getUniqueId());
      Player killer = entity.getKiller();
      this.payOut(entity, def, damage, killer);
      this.announce(def.displayName + " has been slain" + (killer != null ? " by " + killer.getName() : "") + "!", NamedTextColor.GOLD);
   }

   /**
    * Everyone who dealt at least min-damage-share of the total (and the killer) gets a personal loot roll straight
    * into their inventory, and the Stratas reward is split by damage share. Nothing lands on the ground to steal.
    */
   private void payOut(LivingEntity boss, BossDefinition def, Map<UUID, Double> damage, Player killer) {
      Map<UUID, Double> eligible = new HashMap<>();
      double total = 0.0;
      if (damage != null) for (double d : damage.values()) total += d;
      double minShare = this.plugin.getConfig().getDouble("rewards.min-damage-share", 0.05);
      if (damage != null && total > 0.0) {
         for (Map.Entry<UUID, Double> e : damage.entrySet()) {
            if (e.getValue() / total >= minShare) eligible.put(e.getKey(), e.getValue());
         }
      }
      if (killer != null && !eligible.containsKey(killer.getUniqueId())) {
         eligible.put(killer.getUniqueId(), damage != null ? damage.getOrDefault(killer.getUniqueId(), 0.0) : 0.0);
      }
      if (eligible.isEmpty()) return;
      double pool = 0.0;
      for (double d : eligible.values()) pool += d;
      StrataApi stratas = StrataApi.get();
      Economy vault = stratas == null ? this.economy() : null;
      for (Map.Entry<UUID, Double> e : eligible.entrySet()) {
         Player player = Bukkit.getPlayer(e.getKey());
         if (player == null) continue;
         double share = pool > 0.0 ? e.getValue() / pool : 1.0 / eligible.size();
         long reward = Math.round(def.stratasReward * share);
         if (stratas != null) stratas.deposit(player.getUniqueId(), reward);
         else if (vault != null) vault.depositPlayer(player, reward);
         player.sendMessage(Component.text(def.displayName + " defeated! ", NamedTextColor.GOLD)
            .append(Component.text("+" + (stratas != null ? stratas.format(reward) : reward + " Stratas") + " (" + Math.round(share * 100) + "% of the damage)", NamedTextColor.YELLOW)));
         List<ItemStack> loot = new ArrayList<>();
         this.rollLoot(loot, def);
         for (ItemStack item : loot) {
            for (ItemStack left : player.getInventory().addItem(item).values()) {
               player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
         }
         StrataMMO mmo = this.plugin.core().module(StrataMMO.class);
         if (mmo != null) mmo.bossKill(player, def.id);
      }
   }

   public record BossInfo(UUID id, String bossId, String displayName, org.bukkit.Location location, double health) {
   }

   public List<BossInfo> activeBosses() {
      List<BossInfo> out = new ArrayList<>();
      for (Map.Entry<UUID, ActiveBoss> e : this.active.entrySet()) {
         Entity entity = Bukkit.getEntity(e.getKey());
         if (entity instanceof LivingEntity living && living.isValid()) {
            out.add(new BossInfo(e.getKey(), e.getValue().def().id, e.getValue().def().displayName, living.getLocation(), living.getHealth()));
         }
      }
      return out;
   }

   /** Removes the boss without rewards or cooldown; its timers, bar and arena are cleaned up on the next bar tick. */
   public boolean removeBoss(UUID id) {
      Entity entity = Bukkit.getEntity(id);
      if (entity == null) return false;
      for (Entity passenger : List.copyOf(entity.getPassengers())) passenger.remove();
      entity.remove();
      return true;
   }

   private Economy economy() {
      RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
      return rsp != null ? (Economy)rsp.getProvider() : null;
   }

   private record ActiveBoss(BossDefinition def, World world, BossBar bar, BukkitTask barTask, BukkitTask abilityTask) {
   }
}
