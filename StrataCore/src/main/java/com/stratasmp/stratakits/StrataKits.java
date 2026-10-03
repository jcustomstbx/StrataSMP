package com.stratasmp.stratakits;

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;


public final class StrataKits extends StrataModule {
   public StrataKits(StrataCore core) {
      super(core, "StrataKits");
   }


    private ClaimStore claims;

    @Override
    public void onDisable() {
        if (claims != null) {
            claims.shutdown();
        }
        super.onDisable();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        KitCatalog catalog = new KitCatalog(this);
        claims = new ClaimStore(this);
        KitService service = new KitService(this, catalog, claims);
        KitGui gui = new KitGui(service);
        RankLimits limits = new RankLimits(this);
        RankShowcase showcase = new RankShowcase(this, service, gui, limits);
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(showcase, this);

        KitCommand command = new KitCommand(service, gui, showcase, () -> {
            reloadConfig();
            catalog.reload();
            limits.reload();
        });
        getCommand("kit").setExecutor(command);
        getCommand("kit").setTabCompleter(command);
        getCommand("rankpreview").setExecutor(command);
    }
}
