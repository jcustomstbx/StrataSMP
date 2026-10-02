package com.stratasmp.stratacratevault;

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;

import org.bukkit.entity.Player;

public final class StrataCrateVault extends StrataModule {
   public StrataCrateVault(StrataCore core) {
      super(core, "StrataCrateVault");
   }


    private VaultStore store;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.store = new VaultStore(this);
        VaultGui gui = new VaultGui(this, store);
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(new ReminderListener(this, store), this);
        getCommand("cratevault").setExecutor(new CrateVaultCommand(this, store, gui));
        getLogger().info("StrataCrateVault enabled.");
    }

    boolean isVaultWorld(Player player) {
        String world = player.getWorld().getName();
        return getConfig().getStringList("allowed-worlds").stream().anyMatch(w -> w.equalsIgnoreCase(world));
    }
}
