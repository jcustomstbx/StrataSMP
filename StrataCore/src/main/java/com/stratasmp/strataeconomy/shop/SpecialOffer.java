package com.stratasmp.strataeconomy.shop;

import org.bukkit.Material;

import java.util.List;

/** A shop purchase that isn't a plain {@link Material} - grants its reward by running a console command. */
public final class SpecialOffer {

    public final String id;
    public final String name;
    public final Material icon;
    public final long price;
    public final List<String> lore;
    public final String command;

    public SpecialOffer(String id, String name, Material icon, long price, List<String> lore, String command) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.price = price;
        this.lore = lore;
        this.command = command;
    }
}
