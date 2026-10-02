package com.stratasmp.strataperks;

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;

import com.stratasmp.strataperks.papi.StrataPerksExpansion;

public final class StrataPerks extends StrataModule {
   public StrataPerks(StrataCore core) {
      super(core, "StrataPerks");
   }


    private StrataPerksService strataperks;
    private GodShopCatalog catalog;
    private Msg msg;
    private GodShopGui shopGui;
    private StrataPerksExpansion expansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.msg = new Msg(this);
        this.strataperks = new StrataPerksService(this);
        this.catalog = new GodShopCatalog(this);
        this.shopGui = new GodShopGui(this);

        getServer().getPluginManager().registerEvents(shopGui, this);
        getCommand("strataperks").setExecutor(new StrataPerksCommand(this));
        getCommand("givestrataperks").setExecutor(new GiveStrataPerksCommand(this));
        getCommand("strataperkshop").setExecutor(shopGui);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            var papi = me.clip.placeholderapi.PlaceholderAPIPlugin.getInstance();
            if (papi != null) {
                var previous = papi.getLocalExpansionManager().getExpansion("strataperks");
                if (previous != null && previous.getClass().getName().equals(StrataPerksExpansion.class.getName())) previous.unregister();
            }
            this.expansion = new StrataPerksExpansion(this);
            if (this.expansion.register()) getLogger().info("PlaceholderAPI expansion 'strataperks' registered.");
            else getLogger().warning("Couldn't register PlaceholderAPI expansion 'strataperks'.");
        }

        getLogger().info("StrataPerks enabled - " + catalog.all().size() + " item(s) in the shop.");
    }

    @Override
    public void onDisable() {
        if (expansion != null && expansion.isRegistered()) expansion.unregister();
    }

    public StrataPerksService strataperks() {
        return strataperks;
    }

    public GodShopCatalog catalog() {
        return catalog;
    }

    public Msg msg() {
        return msg;
    }

    public String currencyName(long amount) {
        String key = amount == 1 ? "currency.name-singular" : "currency.name-plural";
        return getConfig().getString(key, amount == 1 ? "StrataPerk" : "StrataPerks");
    }
}
