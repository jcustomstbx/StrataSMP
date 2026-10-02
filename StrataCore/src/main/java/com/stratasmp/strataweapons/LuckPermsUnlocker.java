package com.stratasmp.strataweapons;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Kept in its own class so LuckPerms is only ever class-loaded when a charm is actually redeemed. */
final class LuckPermsUnlocker {

    private LuckPermsUnlocker() {
    }

    /** Completes once the strataskins.&lt;key&gt; node is saved; completes exceptionally if it couldn't be. */
    static CompletableFuture<Void> grant(UUID uuid, String key) {
        return LuckPermsProvider.get().getUserManager()
                .modifyUser(uuid, user -> user.data().add(Node.builder("strataskins." + key).build()));
    }
}
