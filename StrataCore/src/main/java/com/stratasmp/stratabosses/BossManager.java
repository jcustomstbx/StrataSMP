package com.stratasmp.stratabosses;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
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
   private final double weaponDropChance;
   private final List<LootRoll> supplyLoot = new ArrayList<>();
   private final long idleDespawnMillis;
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
      this.idleDespawnMillis = Math.max(0L, plugin.getConfig().getLong("spawn-checks.idle-despawn-minutes", 15L)) * 60000L;
      ConfigurationSection lootSection = plugin.getConfig().getConfigurationSection("loot");
      this.weaponDropChance = lootSection != null ? lootSection.getDouble("weapon-drop-chance", 0.08) : 0.08;
      ConfigurationSection suppliesSection = lootSection != null ? lootSection.getConfigurationSection("supplies") : null;
      if (suppliesSection != null) {
         for (String key : suppliesSection.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
               plugin.getLogger().warning("Unknown material in loot.supplies: " + key);
            } else {
               this.supplyLoot.add(LootRoll.fromConfig(suppliesSection.getConfigurationSection(key), material));
            }
         }
      }
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
         maxHealthAttr.setBaseValue(def.maxHealth);
         entity.setHealth(def.maxHealth);
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
      BossAbilities.clearState(id);
      BossArenaBuilder.restore(this.releaseArena(id));
      boss.remove();
      Bukkit.broadcastMessage(def.displayName + " loses interest and melts back into the world.");
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
            Bukkit.broadcastMessage(def.displayName + " is enraged!");
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
      NamespacedKey marker = new NamespacedKey(this.plugin, "boss-projectile");
      if (event.getEntity().getPersistentDataContainer().has(marker, PersistentDataType.BYTE)) {
         event.blockList().clear();
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onItemPickup(EntityPickupItemEvent event) {
      if (!(event.getEntity() instanceof Player)) {
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
      this.rollLoot(event.getDrops(), def);
      Player killer = entity.getKiller();
      if (killer != null) {
         Economy economy = this.economy();
         if (economy != null) {
            economy.depositPlayer(killer, def.stratasReward);
            killer.sendMessage(def.displayName + " defeated! +" + economy.format(def.stratasReward));
         }
      }

      Bukkit.broadcastMessage(def.displayName + " has been slain" + (killer != null ? " by " + killer.getName() : "") + "!");
   }

   private Economy economy() {
      RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
      return rsp != null ? (Economy)rsp.getProvider() : null;
   }

   private record ActiveBoss(BossDefinition def, World world, BossBar bar, BukkitTask barTask, BukkitTask abilityTask) {
   }
}
