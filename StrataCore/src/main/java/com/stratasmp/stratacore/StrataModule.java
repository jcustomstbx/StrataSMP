package com.stratasmp.stratacore;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.plugin.configuration.PluginMeta;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;

/** Shared compatibility context for a feature previously shipped as its own plugin. */
public abstract class StrataModule implements Plugin, TabExecutor {
    private final StrataCore core;
    private final String moduleName;
    private final File dataFolder;
    private final Logger logger;
    private FileConfiguration config;
    private boolean enabled;
    private boolean naggable = true;

    protected StrataModule(StrataCore core, String moduleName) {
        this.core = core;
        this.moduleName = moduleName;
        this.dataFolder = new File(core.getDataFolder().getParentFile(), moduleName);
        this.logger = Logger.getLogger("StrataCore/" + moduleName);
    }

    public abstract void onEnable();

    public void onDisable() { enabled = false; }

    public final StrataCore core() { return core; }
    public final Server getServer() { return core.getServer(); }
    public final Logger getLogger() { return logger; }
    public final String getName() { return moduleName; }
    @Override public final String namespace() { return moduleName.toLowerCase(Locale.ROOT); }
    public final File getDataFolder() { return dataFolder; }
    public final PluginCommand getCommand(String name) { return core.getCommand(name); }
    @Override public final PluginDescriptionFile getDescription() { return core.getDescription(); }
    @Override public final PluginMeta getPluginMeta() { return core.getPluginMeta(); }
    @Override public final PluginLoader getPluginLoader() { return core.getPluginLoader(); }
    @Override public final LifecycleEventManager<Plugin> getLifecycleManager() {
        @SuppressWarnings("unchecked") LifecycleEventManager<Plugin> manager =
                (LifecycleEventManager<Plugin>) (LifecycleEventManager<?>) core.getLifecycleManager();
        return manager;
    }
    @Override public final boolean isEnabled() { return enabled; }
    @Override public final boolean isNaggable() { return naggable; }
    @Override public final void setNaggable(boolean naggable) { this.naggable = naggable; }
    @Override public final void onLoad() {}
    @Override public final ChunkGenerator getDefaultWorldGenerator(String worldName, String id) { return null; }
    @Override public final BiomeProvider getDefaultBiomeProvider(String worldName, String id) { return null; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) { return false; }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { return List.of(); }

    final void startModule() { enabled = true; }
    final void stopModule() { enabled = false; }

    final void unregisterServices() {
        core.getServer().getServicesManager().unregisterAll(this);
    }

    public final FileConfiguration getConfig() {
        if (config == null) reloadConfig();
        return config;
    }

    public final void reloadConfig() {
        File file = new File(dataFolder, "config.yml");
        config = YamlConfiguration.loadConfiguration(file);
        try (InputStream defaults = getResource("config.yml")) {
            if (defaults != null) {
                YamlConfiguration fallback = YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(defaults, java.nio.charset.StandardCharsets.UTF_8));
                config.setDefaults(fallback);
            }
        } catch (IOException e) {
            logger.warning("Could not load default config: " + e.getMessage());
        }
    }

    public final void saveDefaultConfig() {
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Could not create module data folder " + dataFolder);
        }
        File file = new File(dataFolder, "config.yml");
        if (!file.exists()) saveResource("config.yml", false);
        reloadConfig();
    }

    public final void saveConfig() {
        try {
            getConfig().save(new File(dataFolder, "config.yml"));
        } catch (IOException e) {
            logger.severe("Could not save config.yml: " + e.getMessage());
        }
    }

    public final InputStream getResource(String name) {
        return core.getResource("modules/" + moduleName + "/" + name);
    }

    public final void saveResource(String name, boolean replace) {
        File out = new File(dataFolder, name);
        if (out.exists() && !replace) return;
        File parent = out.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create module resource directory " + parent);
        }
        try (InputStream in = getResource(name)) {
            if (in == null) throw new IllegalArgumentException("Missing embedded resource " + name);
            Files.copy(in, out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save module resource " + name, e);
        }
    }

    public final JavaPlugin hostPlugin() { return core; }
}
