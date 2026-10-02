package com.stratasmp.stratabosses;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

/** Uses the same optional, reflective ModelEngine v4 integration as the Pets Plugin. */
final class ModelEngineBridge {
    private static final Set<String> HAND_RIG_MODELS = Set.of("zekka", "vaelspire", "cinderjaw", "abyssal_coilfang");
    private static final class Handle {
        final LivingEntity carrier;
        final Object modeled;
        final Object active;
        final Object weapon;
        final boolean wasVisible;
        final boolean combatStance;
        final boolean rigidGrip;
        Location previous;
        String locomotion = "";
        long effectUntil;
        boolean throwing;
        boolean melee;
        Handle(LivingEntity carrier, Object modeled, Object active, Object weapon, boolean wasVisible, String modelId) {
            this.carrier = carrier;
            this.modeled = modeled;
            this.active = active;
            this.weapon = weapon;
            this.wasVisible = wasVisible;
            this.combatStance = "vaelspire".equals(modelId);
            this.rigidGrip = HAND_RIG_MODELS.contains(modelId);
            this.previous = carrier.getLocation().clone();
        }
        List<Object> parts() { return weapon == null ? List.of(active) : List.of(active, weapon); }
    }

    private final Logger logger;
    private final Map<UUID, Handle> handles = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private Method createModel, createEntity, addModel, visible, isVisible, saved, destroy;
    private Method handler, play, stop, tint, getBone, boneVisible, boneLocation;
    private Method forceStop, getAnimation, animationTime, setDefaultProperty;
    private java.lang.reflect.Constructor<?> defaultProperty;
    private Object idleState, walkState;
    private boolean available;

    ModelEngineBridge(Logger logger, boolean enabled) {
        this.logger = logger;
        if (!enabled) return;
        try {
            Class<?> api = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Class<?> modeled = Class.forName("com.ticxo.modelengine.api.model.ModeledEntity");
            Class<?> active = Class.forName("com.ticxo.modelengine.api.model.ActiveModel");
            Class<?> animation = Class.forName("com.ticxo.modelengine.api.animation.handler.AnimationHandler");
            Class<?> bone = Class.forName("com.ticxo.modelengine.api.model.bone.ModelBone");
            createModel = api.getMethod("createActiveModel", String.class);
            createEntity = api.getMethod("getOrCreateModeledEntity", Entity.class);
            addModel = modeled.getMethod("addModel", active, boolean.class);
            visible = modeled.getMethod("setBaseEntityVisible", boolean.class);
            isVisible = modeled.getMethod("isBaseEntityVisible");
            saved = modeled.getMethod("setSaved", boolean.class);
            destroy = modeled.getMethod("destroy");
            handler = active.getMethod("getAnimationHandler");
            tint = active.getMethod("setDefaultTint", Color.class);
            play = animation.getMethod("playAnimation", String.class, double.class, double.class, double.class, boolean.class);
            stop = animation.getMethod("stopAnimation", String.class);
            forceStop = animation.getMethod("forceStopAnimation", String.class);
            Class<?> state = Class.forName("com.ticxo.modelengine.api.animation.ModelState");
            Class<?> property = Class.forName("com.ticxo.modelengine.api.animation.handler.AnimationHandler$DefaultProperty");
            defaultProperty = property.getConstructor(state, String.class, double.class, double.class, double.class);
            idleState = state.getField("IDLE").get(null); walkState = state.getField("WALK").get(null);
            setDefaultProperty = animation.getMethod("setDefaultProperty", property);
            getAnimation = animation.getMethod("getAnimation", String.class);
            animationTime = Class.forName("com.ticxo.modelengine.api.animation.property.IAnimationProperty").getMethod("getTime");
            getBone = active.getMethod("getBone", String.class);
            boneVisible = bone.getMethod("setVisible", boolean.class);
            boneLocation = bone.getMethod("getLocation");
            available = true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            warn("api", "ModelEngine v4 API unavailable; using vanilla boss visuals.", exception);
        }
    }

    boolean attach(LivingEntity carrier, String modelId) {
        return attach(carrier, modelId, "");
    }

    boolean attach(LivingEntity carrier, String modelId, String weaponModelId) {
        if (!available || modelId == null || modelId.isBlank()) return false;
        if (handles.containsKey(carrier.getUniqueId())) return true;
        Object modeled = null;
        boolean wasVisible = true;
        try {
            // Validate/create the model before touching the carrier, so missing blueprints
            // leave the original mob and its appearance intact.
            Object active = createModel.invoke(null, modelId);
            if (HAND_RIG_MODELS.contains(modelId)) {
                // The plugin owns locomotion. Native automatic idle/walk loops
                // otherwise blend over combat poses and independent previews.
                Object animations = handler.invoke(active);
                for (Object state : List.of(idleState, walkState))
                    setDefaultProperty.invoke(animations, defaultProperty.newInstance(state, "__strata_manual_locomotion", 0D, 0D, 1D));
            }
            // Built-in bosses hold distinct weapon bones on the wrist's
            // same rig. A second ActiveModel has an independent animation clock
            // and automatic loops, which let the sword slip through the fist.
            Object weapon = HAND_RIG_MODELS.contains(modelId) || weaponModelId == null || weaponModelId.isBlank()
                    ? null : createModel.invoke(null, weaponModelId);
            modeled = createEntity.invoke(null, carrier);
            wasVisible = (boolean) isVisible.invoke(modeled);
            addModel.invoke(modeled, active, true); // Use the body's fitted collision box.
            if (weapon != null) addModel.invoke(modeled, weapon, false);
            saved.invoke(modeled, false); // StrataBosses reattaches its own persistent bosses.
            visible.invoke(modeled, false); // Hide native armor/weapons as well as the mob.
            Handle handle = new Handle(carrier, modeled, active, weapon, wasVisible, modelId);
            handles.put(carrier.getUniqueId(), handle);
            locomotion(handle, "idle");
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            handles.remove(carrier.getUniqueId());
            if (modeled != null) restore(modeled, wasVisible);
            warn("model:" + modelId, "Could not attach boss model '" + modelId
                    + "'. Install its blueprint and reload models; vanilla visuals remain active.", exception);
            return false;
        }
    }

    void tick() {
        for (Handle h : handles.values()) {
            if (!h.carrier.isValid() || h.carrier.isDead()) continue;
            Location now = h.carrier.getLocation();
            double dx = now.getX() - h.previous.getX(), dz = now.getZ() - h.previous.getZ();
            boolean moving = now.getWorld().equals(h.previous.getWorld()) && dx * dx + dz * dz > .0025;
            if (!h.melee && System.nanoTime() >= h.effectUntil) {
                h.throwing = false;
                LivingEntity target = h.combatStance && h.carrier instanceof Mob mob ? mob.getTarget() : null;
                boolean combat = target != null && target.isValid() && !target.isDead()
                    && target.getWorld().equals(h.carrier.getWorld());
                locomotion(h, (combat ? "combat_" : "") + (moving ? "walk" : "idle"));
            }
            h.previous = now.clone();
        }
    }

    private void locomotion(Handle h, String name) {
        if (name.equals(h.locomotion)) return;
        try {
            for (Object part : h.parts()) {
                Object animations = handler.invoke(part);
                if (!h.locomotion.isEmpty()) (h.rigidGrip ? forceStop : stop).invoke(animations, h.locomotion);
                // Hand-rig loops contain the complete held-item pose. Blending
                // both loops through the default arm pose makes the sword dip on
                // every start/stop, so hand off those loops directly.
                double blend = h.rigidGrip ? 0D : .15D;
                play.invoke(animations, name, blend, blend, 1D, false);
            }
            h.locomotion = name;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warn("animation", "Could not update boss animation.", exception);
        }
    }

    void attack(LivingEntity carrier) { animate(carrier.getUniqueId(), "attack"); }

    boolean hasModel(LivingEntity carrier) { return handles.containsKey(carrier.getUniqueId()); }

    boolean beginMelee(LivingEntity carrier) {
        Handle h = handles.get(carrier.getUniqueId());
        if (h == null || h.melee || (h.throwing && System.nanoTime() < h.effectUntil)) return false;
        try {
            for (Object part : h.parts()) {
                Object animations = handler.invoke(part);
                for (String name : List.of("attack", "enrage", "idle", "walk", "combat_idle", "combat_walk")) forceStop.invoke(animations, name);
                play.invoke(animations, "attack", 0D, .1D, 1D, true);
            }
            h.locomotion = "";
            h.melee = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warn("melee", "Could not start synchronized melee animation.", exception);
            return false;
        }
    }

    double meleeTime(LivingEntity carrier) {
        Handle h = handles.get(carrier.getUniqueId());
        if (h == null || !h.melee) return -1;
        try {
            Object animation = getAnimation.invoke(handler.invoke(h.active), "attack");
            // SimpleProperty starts at -1 until ModelEngine's first prepare tick.
            // AI can start a swing after that tick, so this is a live windup, not
            // a missing/cancelled animation. Reserve -1 for genuinely absent swings.
            return animation == null ? -1 : Math.max(0, ((Number) animationTime.invoke(animation)).doubleValue());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warn("melee-clock", "Could not read melee animation time; cancelling the pending hit.", exception);
            return -1;
        }
    }

    void finishMelee(LivingEntity carrier) {
        Handle h = handles.get(carrier.getUniqueId());
        if (h != null && h.melee) { h.melee = false; h.effectUntil = 0; }
    }

    void throwWeapon(LivingEntity carrier) { animate(carrier.getUniqueId(), "throw"); }

    void weaponVisible(LivingEntity carrier, boolean show) {
        Handle h = handles.get(carrier.getUniqueId());
        if (h == null) return;
        try {
            Optional<?> bone = (Optional<?>) getBone.invoke(h.weapon == null ? h.active : h.weapon, "weapon");
            if (bone.isPresent()) boneVisible.invoke(bone.get(), show);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warn("weapon-visibility", "Could not update held boss weapon.", exception);
        }
    }

    Location weaponLocation(LivingEntity carrier) {
        Handle h = handles.get(carrier.getUniqueId());
        if (h != null) {
            try {
                Optional<?> bone = (Optional<?>) getBone.invoke(h.weapon == null ? h.active : h.weapon, "weapon");
                if (bone.isPresent()) return ((Location) boneLocation.invoke(bone.get())).clone();
            } catch (ReflectiveOperationException | RuntimeException exception) {
                warn("weapon-location", "Could not locate weapon grip; using the boss eye location.", exception);
            }
        }
        return carrier.getEyeLocation();
    }

    void enrage(LivingEntity carrier) {
        Handle h = handles.get(carrier.getUniqueId());
        if (h == null) return;
        try {
            tint.invoke(h.active, Color.fromRGB(255, 170, 150));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warn("tint", "Could not apply boss enrage tint.", exception);
        }
        animate(carrier.getUniqueId(), "enrage");
    }

    private void animate(UUID id, String name) {
        Handle h = handles.get(id);
        if (h == null) return;
        long now = System.nanoTime();
        if (h.melee && !name.equals("throw")) return;
        if (h.throwing && now < h.effectUntil) return;
        if (name.equals("attack") && now < h.effectUntil) return;
        try {
            for (Object part : h.parts()) {
                Object animations = handler.invoke(part);
                if (name.equals("throw")) {
                    stop.invoke(animations, "attack");
                    stop.invoke(animations, "enrage");
                    if (!h.locomotion.isEmpty()) stop.invoke(animations, h.locomotion);
                }
                play.invoke(animations, name, name.equals("throw") ? 0D : .1D, .2D, 1D, true);
            }
            if (name.equals("throw")) { h.melee = false; h.throwing = true; h.locomotion = ""; }
            h.effectUntil = now + (name.equals("enrage") ? 1_200_000_000L : name.equals("throw") ? 1_500_000_000L : 800_000_000L);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warn("animation:" + name, "Could not play boss animation '" + name + "'.", exception);
        }
    }

    void detach(UUID id) {
        Handle h = handles.remove(id);
        if (h != null) restore(h.modeled, h.wasVisible);
    }

    void close() {
        for (UUID id : Set.copyOf(handles.keySet())) detach(id);
    }

    private void restore(Object modeled, boolean wasVisible) {
        try { visible.invoke(modeled, wasVisible); }
        catch (ReflectiveOperationException | RuntimeException exception) {
            warn("visibility", "Could not restore native boss visibility.", exception);
        }
        try { destroy.invoke(modeled); }
        catch (ReflectiveOperationException | RuntimeException exception) {
            warn("cleanup", "Could not remove a boss model renderer.", exception);
        }
    }

    private void warn(String key, String message, Throwable exception) {
        if (warned.add(key)) logger.log(Level.WARNING, message, exception);
    }
}
