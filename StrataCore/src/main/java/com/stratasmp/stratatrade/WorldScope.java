/*
 * Copyright (c) 2026 JCustoms. All Rights Reserved.
 *
 * This file is proprietary and confidential. No use, copying, modification,
 * or distribution of this file or its compiled output, by any means, is
 * permitted without the prior written permission of JCustoms.
 *
 * See the LICENSE file distributed with this project for the full terms.
 */

package com.stratasmp.stratatrade;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.World;

public final class WorldScope {
    private static volatile Set<String> enabled = Set.of();

    private WorldScope() {
    }

    public static void load(List<String> worlds) {
        enabled = new HashSet<>(worlds);
    }

    public static boolean allows(World world) {
        return world != null && (enabled.isEmpty() || enabled.contains(world.getName()));
    }
}
