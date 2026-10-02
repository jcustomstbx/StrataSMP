package com.stratasmp.stratakits;

import org.bukkit.Material;

import java.util.List;

record Kit(String id, String displayName, Material icon, List<KitItem> items, List<Reward> rewards) {

    int stacksNeeded() {
        int total = 0;
        for (KitItem item : items) {
            total += item.stackCount();
        }
        return total;
    }
}