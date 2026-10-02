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

import com.stratasmp.stratacore.StrataCore;
import com.stratasmp.stratacore.StrataModule;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.h2.jdbcx.JdbcDataSource;
import javax.sql.DataSource;
import java.io.File;

public final class StrataTradePlugin extends StrataModule {
   public StrataTradePlugin(StrataCore core) {
      super(core, "StrataTrade");
   }


    private TradeManager manager;
    private TradeRepository repository;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        WorldScope.load(getConfig().getStringList("enabled-worlds"));

        var economy = resolveEconomy();
        if (economy == null) {
            getLogger().severe("No Vault economy provider found, disabling.");
            throw new IllegalStateException("StrataTrade requires an active Vault economy provider.");
        }

        repository = new TradeRepository(createDataSource(), getLogger());
        repository.ensureSchema();

        var items = new TradeItems(getConfig().getStringList("blocked-materials"));
        manager = new TradeManager(this, repository, economy, items);
        manager.start();
        getServer().getPluginManager().registerEvents(new TradeListener(manager), this);
        getServer().getOnlinePlayers().forEach(manager::restoreOnJoin);

        var handler = new TradeCommand(this, manager, repository);
        for (var name : new String[] {"trade", "stratatrade"}) {
            var command = getCommand(name);
            if (command != null) {
                command.setExecutor(handler);
                command.setTabCompleter(handler);
            }
        }
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.cancelAll("The trade was stopped.");
        }
        if (repository != null) {
            repository.shutdown();
        }
    }

    private Economy resolveEconomy() {
        RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider();
    }

    private DataSource createDataSource() {
        var data = new File(getDataFolder(), "data");
        if (!data.mkdirs() && !data.isDirectory()) {
            throw new IllegalStateException("Could not create StrataTrade's database folder.");
        }
        var database = new File(data, "stratatrade").getAbsolutePath();
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:file:" + database + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        source.setUser("sa");
        source.setPassword("");
        return source;
    }
}
