package com.stratasmp.stratakits;

import org.bukkit.Material;

/** Something a kit hands over by console command (stratas, crate keys) rather than as an item. */
record Reward(String display, Material icon, String command) {
}