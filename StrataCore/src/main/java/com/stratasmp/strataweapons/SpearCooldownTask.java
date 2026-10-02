package com.stratasmp.strataweapons;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import com.stratasmp.stratacore.StrataModule;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Two independent, separately-toggled spear tweaks - either can be on without the other:
 * - {@code spear.remove-attack-cooldown}: takes the vanilla jab (attack-speed) cooldown off while a
 *   spear is held, via a transient ATTACK_SPEED bonus.
 * - {@code spear.clear-lunge-cooldown}: bypasses RankEssentials' own custom 15 second post-Lunge lockout
 *   entirely (that lockout is RankEssentials' own addition, not part of vanilla Minecraft at all), so
 *   the only thing left limiting how often Lunge can be used is vanilla's own kinetic-weapon charge-up
 *   time baked into the spear item itself - true Minecraft default, with RankEssentials' extra
 *   restriction removed rather than throttled.
 */
public final class SpearCooldownTask extends BukkitRunnable {

    private static final double BONUS = 100.0;

    private final StrataModule plugin;
    private final NamespacedKey modifierKey;
    private Class<?> managerClass;
    private Method isOnCooldown;
    private Method finish;
    private Method clearRight;
    private boolean lungeBroken;

    public SpearCooldownTask(StrataModule plugin) {
        this.plugin = plugin;
        this.modifierKey = new NamespacedKey(plugin, "no_spear_cooldown");
    }

    public void start() {
        runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void run() {
        if (plugin.getConfig().getBoolean("spear.clear-lunge-cooldown", false)) {
            clearLungeCooldowns();
        }
        boolean removeAttackCooldown = plugin.getConfig().getBoolean("spear.remove-attack-cooldown", false);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            AttributeInstance speed = player.getAttribute(Attribute.ATTACK_SPEED);
            if (speed == null) {
                continue;
            }
            boolean holding = removeAttackCooldown && isSpear(player.getInventory().getItemInMainHand());
            boolean applied = speed.getModifier(modifierKey) != null;
            if (holding && !applied) {
                speed.addTransientModifier(new AttributeModifier(modifierKey, BONUS,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
            } else if (!holding && applied) {
                speed.removeModifier(modifierKey);
            }
        }
    }

    /**
     * RankEssentials starts a fixed 15 second cooldown (LungeCooldownManager.COOLDOWN_SECONDS, not
     * configurable) after every Lunge swing and cancels swings while it runs. Its manager is reached by
     * reflection and any running cooldown is finished immediately and its action-bar countdown cleared -
     * no throttle, no interval, RankEssentials' own restriction just never gets to apply.
     */
    private void clearLungeCooldowns() {
        if (lungeBroken) {
            return;
        }
        Plugin rankEssentials = plugin.getServer().getPluginManager().getPlugin("RankEssentials");
        if (rankEssentials == null || !rankEssentials.isEnabled()) {
            return;
        }
        try {
            Object manager = rankEssentials.getClass().getMethod("getLungeCooldownManager").invoke(rankEssentials);
            if (manager == null) {
                return;
            }
            Object actionBar = rankEssentials.getClass().getMethod("getActionBarManager").invoke(rankEssentials);
            if (managerClass != manager.getClass()) {
                managerClass = manager.getClass();
                isOnCooldown = managerClass.getMethod("isOnCooldown", UUID.class);
                finish = managerClass.getDeclaredMethod("finish", UUID.class);
                finish.setAccessible(true);
                clearRight = actionBar == null ? null : actionBar.getClass().getMethod("clearRight", UUID.class);
            }
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                UUID id = player.getUniqueId();
                if ((boolean) isOnCooldown.invoke(manager, id)) {
                    finish.invoke(manager, id);
                    // finish() only stops the timer; the countdown it last drew stays on the action bar until cleared
                    if (clearRight != null) {
                        clearRight.invoke(actionBar, id);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            lungeBroken = true;
            plugin.getLogger().warning("Couldn't clear RankEssentials' Lunge cooldown, leaving it alone: " + e);
        }
    }

    /** Stops the task and takes the bonus back off everyone, for plugin disable and reload. */
    public void shutdown() {
        cancel();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            AttributeInstance speed = player.getAttribute(Attribute.ATTACK_SPEED);
            if (speed != null && speed.getModifier(modifierKey) != null) {
                speed.removeModifier(modifierKey);
            }
        }
    }

    private static boolean isSpear(ItemStack item) {
        return item != null && item.getType().name().endsWith("_SPEAR");
    }
}
