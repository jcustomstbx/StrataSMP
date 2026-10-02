package com.stratasmp.stratabosses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.persistence.PersistentDataType;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

/** Defers native melee until the model's actual attack clock reaches contact. */
final class BossMelee implements Listener {
    private final StrataModule plugin;
    private final ModelEngineBridge models;
    private final NamespacedKey bossIdKey;
    private final Map<UUID, Swing> swings = new HashMap<>();

    BossMelee(StrataModule plugin, ModelEngineBridge models, NamespacedKey bossIdKey) {
        this.plugin = plugin; this.models = models; this.bossIdKey = bossIdKey;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (event.getCause() != DamageCause.ENTITY_ATTACK
                || !(event.getDamager() instanceof LivingEntity boss)
                || !(event.getEntity() instanceof LivingEntity target)
                || !models.hasModel(boss) || BossAbilities.isAbilityDamage(boss.getUniqueId())) return;
        String id = boss.getPersistentDataContainer().get(bossIdKey, PersistentDataType.STRING);
        if (id == null) return;
        Swing current = swings.get(boss.getUniqueId());
        if (current != null && current.applying) return; // The one native hit at contact.
        event.setCancelled(true);
        if (current != null || !models.beginMelee(boss)) return;
        Swing swing = new Swing(boss, target, id.equals("cinderjaw") ? .2 : .4,
            id.equals("cinderjaw") ? .45 : .8);
        swings.put(boss.getUniqueId(), swing);
        swing.task = plugin.getServer().getScheduler().runTaskTimer(plugin, swing::tick, 1L, 1L);
    }

    private final class Swing {
        final LivingEntity boss, target;
        final double contact, duration;
        BukkitTask task;
        int ticks;
        boolean struck, applying;
        Swing(LivingEntity boss, LivingEntity target, double contact, double duration) {
            this.boss = boss; this.target = target; this.contact = contact; this.duration = duration;
        }
        void tick() {
            if (++ticks > 40 || !boss.isValid() || boss.isDead()) { clear(boss.getUniqueId()); return; }
            double time = models.meleeTime(boss);
            if (time < 0) { clear(boss.getUniqueId()); return; }
            if (!struck && time + .0001 >= contact) {
                struck = true;
                if (time <= contact + .1 && target.isValid() && !target.isDead()
                        && boss.getWorld().equals(target.getWorld()) && inReach(boss, target)
                        && boss.hasLineOfSight(target)) {
                    // Re-enter vanilla combat here: armor, shields, enchantments,
                    // knockback and other plugins all see the hit at the impact pose.
                    applying = true;
                    try { boss.attack(target); }
                    finally { applying = false; }
                }
            }
            if (time + .0001 >= duration) clear(boss.getUniqueId());
        }
    }

    private static boolean inReach(LivingEntity boss, LivingEntity target) {
        BoundingBox a = boss.getBoundingBox(), b = target.getBoundingBox();
        double dx = Math.max(0, Math.max(a.getMinX() - b.getMaxX(), b.getMinX() - a.getMaxX()));
        double dy = Math.max(0, Math.max(a.getMinY() - b.getMaxY(), b.getMinY() - a.getMaxY()));
        double dz = Math.max(0, Math.max(a.getMinZ() - b.getMaxZ(), b.getMinZ() - a.getMaxZ()));
        return dx * dx + dy * dy + dz * dz <= 1.21;
    }

    void clear(UUID id) {
        Swing swing = swings.remove(id);
        if (swing != null) { swing.task.cancel(); models.finishMelee(swing.boss); }
    }

    void close() { for (UUID id : java.util.Set.copyOf(swings.keySet())) clear(id); }
}
