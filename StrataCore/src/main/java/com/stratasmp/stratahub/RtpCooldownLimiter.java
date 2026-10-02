package com.stratasmp.stratahub;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import com.stratasmp.strataranks.RankTier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * RankEssentials' /rtp has a fixed 300 second cooldown (TPManager.RTP_COOLDOWN, not configurable).
 * {@code rtpCooldowns} is a public {@code Map<UUID, Long>} of absolute expiry timestamps, so any entry
 * further out than {@code rtp-cooldown-cap-seconds} just gets pulled back in - the cooldown still counts
 * down and still blocks /rtp in the meantime, it just ends sooner. Only the NPC-triggered RTP
 * ({@link EntryNpcListener}) clears it outright; typing /rtp yourself gets this shorter cooldown instead.
 */
final class RtpCooldownLimiter extends BukkitRunnable {

    private final StrataHub plugin;
    private Field cooldownsField;
    private boolean broken;

    RtpCooldownLimiter(StrataHub plugin) {
        this.plugin = plugin;
    }

    void start() {
        runTaskTimer(plugin, 20L, 20L);
    }

    @Override
    public void run() {
        if (broken) {
            return;
        }
        Plugin rankEssentials = plugin.getServer().getPluginManager().getPlugin("RankEssentials");
        if (rankEssentials == null || !rankEssentials.isEnabled()) {
            return;
        }
        long capSeconds = plugin.getConfig().getLong("rtp-cooldown-cap-seconds", 120L);
        try {
            Object tpManager = rankEssentials.getClass().getMethod("getTPManager").invoke(rankEssentials);
            if (cooldownsField == null) {
                cooldownsField = tpManager.getClass().getField("rtpCooldowns");
            }
            @SuppressWarnings("unchecked")
            Map<UUID, Long> cooldowns = (Map<UUID, Long>) cooldownsField.get(tpManager);
            for (Map.Entry<UUID, Long> entry : cooldowns.entrySet()) {
                Player player = plugin.getServer().getPlayer(entry.getKey());
                long seconds = player != null && RankTier.of(player).ordinal() >= RankTier.ORNATE.ordinal()
                        ? plugin.getConfig().getLong("ornate-rtp-cooldown-seconds", 90L) : capSeconds;
                long cap = System.currentTimeMillis() + seconds * 1000L;
                if (entry.getValue() > cap) {
                    entry.setValue(cap);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            plugin.getLogger().warning("Couldn't cap RankEssentials' /rtp cooldown, leaving it at 5 minutes: " + e);
        }
    }
}
