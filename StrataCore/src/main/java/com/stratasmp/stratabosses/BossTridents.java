package com.stratasmp.stratabosses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Owns windup, one physical projectile, and restoration of the separate held weapon. */
final class BossTridents implements Listener {
    static final long RELEASE_TICKS = 14L; // Matches the .7-second release pose in both blueprints.
    private static final class ThrowState {
        final LivingEntity boss;
        final ItemStack held;
        final List<BukkitTask> tasks = new ArrayList<>();
        boolean released;
        ThrowState(LivingEntity boss) {
            this.boss = boss;
            ItemStack hand = boss.getEquipment().getItemInMainHand();
            this.held = hand.getType() == Material.TRIDENT ? hand.clone() : new ItemStack(Material.TRIDENT);
        }
    }
    private record Shot(UUID owner, Trident entity, BukkitTask expiry) {}
    private final StrataModule plugin;
    private final ModelEngineBridge models;
    private final NamespacedKey bossIdKey;
    private final NamespacedKey projectileKey;
    private final Map<UUID, ThrowState> throwing = new HashMap<>();
    private final Map<UUID, Shot> shots = new HashMap<>();

    BossTridents(StrataModule plugin, ModelEngineBridge models, NamespacedKey bossIdKey) {
        this.plugin = plugin;
        this.models = models;
        this.bossIdKey = bossIdKey;
        this.projectileKey = new NamespacedKey(plugin, "abyssal-trident");
    }

    boolean throwAtNearest(LivingEntity boss) {
        Player nearest = null;
        double distance = 24 * 24;
        for (Player player : boss.getWorld().getPlayers()) {
            if ((player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE)
                    || player.isDead() || !boss.hasLineOfSight(player)) continue;
            double next = player.getLocation().distanceSquared(boss.getLocation());
            if (next < distance) { nearest = player; distance = next; }
        }
        return nearest != null && throwAt(boss, nearest);
    }

    boolean throwAt(LivingEntity boss, LivingEntity target) {
        if (!boss.isValid() || boss.isDead() || boss.getEquipment() == null
                || !target.isValid() || target.isDead() || !boss.getWorld().equals(target.getWorld())
                || throwing.containsKey(boss.getUniqueId())) return false;
        ThrowState state = new ThrowState(boss);
        throwing.put(boss.getUniqueId(), state);
        models.throwWeapon(boss);
        state.tasks.add(plugin.getServer().getScheduler().runTaskLater(plugin, () -> release(state, target), RELEASE_TICKS));
        return true;
    }

    private void release(ThrowState state, LivingEntity target) {
        LivingEntity boss = state.boss;
        if (throwing.get(boss.getUniqueId()) != state) return;
        if (!boss.isValid() || boss.isDead() || !target.isValid() || target.isDead()
                || !boss.getWorld().equals(target.getWorld()) || !boss.hasLineOfSight(target)
                || (target instanceof Player player && player.getGameMode() != GameMode.SURVIVAL
                    && player.getGameMode() != GameMode.ADVENTURE)) {
            restore(state); return;
        }
        try {
            Location hand = models.weaponLocation(boss);
            // Aim at the torso and compensate for projectile drag/gravity over flight time.
            Location targetCenter = target.getLocation().add(0, target.getHeight() * .55, 0);
            Vector aim = targetCenter.toVector().subtract(hand.toVector());
            if (aim.lengthSquared() < .001) { restore(state); return; }
            Location launch = hand.clone().add(aim.clone().normalize().multiply(.6));
            Vector delta = targetCenter.toVector().subtract(launch.toVector());
            double flightTicks = Math.max(1, Math.hypot(delta.getX(), delta.getZ()) / 1.6);
            double dragSum = (1 - Math.pow(.99, flightTicks)) / .01;
            Vector velocity = delta.multiply(1 / dragSum);
            velocity.setY(velocity.getY() + 5 * (flightTicks - dragSum) / dragSum);
            Trident trident = boss.getWorld().spawn(launch, Trident.class, shot -> {
                shot.getPersistentDataContainer().set(projectileKey, PersistentDataType.BYTE, (byte) 1);
                shot.setShooter(boss);
                shot.setItemStack(state.held.clone());
                shot.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                shot.setLoyaltyLevel(0);
                shot.setDamage(8.0 * BossAbilities.damageMultiplier());
                shot.setPersistent(false);
                shot.setVelocity(velocity);
            });
            if (!trident.isValid()) { restore(state); return; }
            BukkitTask expiry = plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> removeShot(trident.getUniqueId()), 200L);
            shots.put(trident.getUniqueId(), new Shot(boss.getUniqueId(), trident, expiry));
            state.released = true;
            models.weaponVisible(boss, false);
            boss.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));
            boss.getWorld().playSound(hand, Sound.ITEM_TRIDENT_THROW, 1.4F, .8F);
            state.tasks.add(plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (throwing.get(boss.getUniqueId()) != state) return;
                restore(state);
                if (boss.isValid() && !boss.isDead()) {
                    boss.getWorld().spawnParticle(Particle.ENCHANT, models.weaponLocation(boss), 15, .15, .2, .15);
                    boss.getWorld().playSound(boss.getLocation(), Sound.ITEM_TRIDENT_RETURN, .8F, 1.3F);
                }
            }, 40L));
        } catch (RuntimeException exception) {
            restore(state);
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not throw boss trident", exception);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVanillaThrow(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident trident) || trident.getPersistentDataContainer().has(projectileKey)
                || !(trident.getShooter() instanceof LivingEntity boss)) return;
        if (!"abyssal_coilfang".equals(boss.getPersistentDataContainer().get(bossIdKey, PersistentDataType.STRING))) return;
        // Vanilla drowned AI uses this same windup/regeneration path instead of firing duplicates.
        event.setCancelled(true);
        throwAtNearest(boss);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        UUID id = event.getEntity().getUniqueId();
        if (shots.containsKey(id)) {
            // Allow vanilla collision damage to finish before removing the spent projectile.
            plugin.getServer().getScheduler().runTask(plugin, () -> removeShot(id));
        }
    }

    private void removeShot(UUID id) {
        Shot shot = shots.remove(id);
        if (shot == null) return;
        shot.expiry().cancel();
        shot.entity().remove();
    }

    private void restore(ThrowState state) {
        if (!throwing.remove(state.boss.getUniqueId(), state)) return;
        state.tasks.forEach(BukkitTask::cancel);
        if (state.boss.isValid() && !state.boss.isDead()) {
            models.weaponVisible(state.boss, true);
            if (state.released && state.boss.getEquipment() != null)
                state.boss.getEquipment().setItemInMainHand(state.held.clone());
        }
    }

    void clear(UUID bossId) {
        ThrowState state = throwing.get(bossId);
        if (state != null) restore(state);
        for (UUID id : new ArrayList<>(shots.keySet()))
            if (shots.get(id).owner().equals(bossId)) removeShot(id);
    }

    void close() {
        for (UUID id : new ArrayList<>(throwing.keySet())) clear(id);
        for (UUID id : new ArrayList<>(shots.keySet())) removeShot(id);
    }
}
