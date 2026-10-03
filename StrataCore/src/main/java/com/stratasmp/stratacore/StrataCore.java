package com.stratasmp.stratacore;

import com.stratasmp.strataeconomy.StrataEconomy;
import com.stratasmp.stratabosses.StrataBosses;
import com.stratasmp.stratacratevault.StrataCrateVault;
import com.stratasmp.stratateams.StrataTeams;
import com.stratasmp.strataweapons.StrataWeapons;
import com.stratasmp.stratahub.StrataHub;
import com.stratasmp.stratakits.StrataKits;
import com.stratasmp.strataranks.StrataRanks;
import com.stratasmp.stratammo.StrataMMO;
import com.stratasmp.strataperks.StrataPerks;
import com.stratasmp.stratatrade.StrataTradePlugin;
import com.stratasmp.stratastore.StrataStore;
import com.stratasmp.strataleaderboards.StrataLeaderboards;
import com.stratasmp.stratakeystones.StrataKeystones;
import com.stratasmp.strataduels.StrataDuels;
import com.stratasmp.stratavotereward.StrataVoteReward;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.plugin.java.JavaPlugin;

public final class StrataCore extends JavaPlugin {
    private final List<StrataModule> enabledModules = new ArrayList<>();
    private final Map<String, StrataModule> modules = new LinkedHashMap<>();

    @Override
    public void onEnable() {
        File pluginData = getDataFolder().getParentFile();
        if (pluginData == null || !pluginData.isDirectory()) {
            getLogger().severe("Could not locate the plugins directory for legacy module data.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        LegacyFolders.migrate(pluginData, getLogger());

        // Economy starts first; MMO and others consume its API.
        List<StrataModule> startupOrder = List.of(
                new StrataEconomy(this),
                new StrataBosses(this),
                new StrataCrateVault(this),
                new StrataTeams(this),
                new StrataWeapons(this),
                new StrataHub(this),
                new StrataKits(this),
                new StrataRanks(this),
                new StrataMMO(this),
                new StrataPerks(this),
                new StrataTradePlugin(this),
                new StrataStore(this),
                new StrataLeaderboards(this),
                new StrataKeystones(this),
                new StrataDuels(this),
                new StrataVoteReward(this));

        for (StrataModule module : startupOrder) {
            modules.put(module.getName(), module);
            module.startModule();
            enabledModules.add(module);
            try {
                module.onEnable();
                getLogger().info("Enabled module " + module.getName());
            } catch (Throwable failure) {
                if (module instanceof StrataEconomy) {
                    // everything pays through the economy, so without it there is nothing safe to run
                    getLogger().severe("StrataEconomy could not start (check the database settings); disabling StrataCore.");
                    failure.printStackTrace();
                    disableModules();
                    getServer().getPluginManager().disablePlugin(this);
                    return;
                }
                getLogger().severe("Module " + module.getName() + " failed to start and was skipped; the rest keep running.");
                failure.printStackTrace();
                shutDown(module);
                releaseCommands(module);
                enabledModules.remove(module);
                modules.remove(module.getName());
            }
        }
    }

    @Override
    public void onDisable() {
        disableModules();
    }

    public StrataModule module(String name) {
        return modules.get(name);
    }

    public <T extends StrataModule> T module(Class<T> type) {
        return modules.values().stream().filter(type::isInstance).map(type::cast).findFirst().orElse(null);
    }

    private void disableModules() {
        for (int i = enabledModules.size() - 1; i >= 0; i--) {
            shutDown(enabledModules.get(i));
        }
        enabledModules.clear();
        modules.clear();
    }

    /** Commands the failed module had already bound must not keep running against a stopped module. */
    private void releaseCommands(StrataModule module) {
        String modulePackage = module.getClass().getPackageName();
        for (String name : getDescription().getCommands().keySet()) {
            org.bukkit.command.PluginCommand command = getCommand(name);
            if (command == null) continue;
            Object executor = command.getExecutor();
            if (executor != this && executor != null && executor.getClass().getName().startsWith(modulePackage)) {
                command.setExecutor(this);
                command.setTabCompleter(null);
            }
        }
    }

    private void shutDown(StrataModule module) {
        try {
            // flag the module as stopped first: shutdown code that checks isEnabled() must take its
            // synchronous path, because the scheduler tasks are cancelled right after onDisable
            module.stopModule();
            module.onDisable();
        } catch (Throwable failure) {
            getLogger().severe("Module " + module.getName() + " failed to shut down cleanly: " + failure);
        } finally {
            getServer().getScheduler().cancelTasks(module);
            org.bukkit.event.HandlerList.unregisterAll(module);
            module.unregisterServices();
            module.stopModule();
        }
    }
}
