package com.stratasmp.stratacore;

import java.util.UUID;
import java.util.function.BiConsumer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import com.destroystokyo.paper.profile.PlayerProfile;

/**
 * Resolves a typed player name to a UUID without ever blocking the main thread. Online and cached players answer
 * immediately; anyone else is looked up on an async worker. Callbacks always run on the main thread. A player who has
 * never joined this server still resolves if their name exists, so grants to them are not silently dropped.
 */
public final class NameLookup {
    private NameLookup() {}

    public static void resolve(StrataModule plugin, String name, BiConsumer<UUID, String> found, Runnable missing) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            found.accept(online.getUniqueId(), online.getName());
            return;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            found.accept(cached.getUniqueId(), cached.getName() == null ? name : cached.getName());
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID id = null;
            String resolved = name;
            try {
                PlayerProfile profile = Bukkit.createProfile(name);
                profile.complete(false);
                id = profile.getId();
                if (profile.getName() != null) resolved = profile.getName();
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Name lookup for '" + name + "' failed: " + e.getMessage());
            }
            UUID foundId = id;
            String foundName = resolved;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (foundId != null) found.accept(foundId, foundName);
                else missing.run();
            });
        });
    }
}
