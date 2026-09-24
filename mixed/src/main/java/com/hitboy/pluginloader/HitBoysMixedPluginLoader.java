package com.hitboy.pluginloader;

import com.hitboy.pluginloader.api.PluginEventBus;
import com.hitboy.pluginloader.bridge.BukkitCommandRegistry;
import com.hitboy.pluginloader.bridge.BukkitEventBridge;
import com.hitboy.pluginloader.core.PluginManager;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * HitBoy's Mixed Compatibility Plugin Loader.
 *
 * <p>A normal Spigot/Paper/Purpur plugin that hosts HitBoy plugins alongside the server's Bukkit
 * plugins. Bukkit plugins keep loading from {@code plugins/}; HitBoy plugins load from
 * {@code plugins/HitBoysMixedPluginLoader/plugins/} and use the same {@code com.hitboy.pluginloader.api}
 * as the standalone loader, so one HitBoy plugin JAR works on both.
 */
public final class HitBoysMixedPluginLoader extends JavaPlugin {
    private PluginEventBus eventBus;
    private PluginManager pluginManager;

    @Override
    public void onEnable() {
        eventBus = new PluginEventBus();
        getServer().getPluginManager().registerEvents(new BukkitEventBridge(eventBus), this);

        BukkitCommandRegistry commandRegistry = new BukkitCommandRegistry(this, getLogger());

        File pluginsDir = new File(getDataFolder(), "plugins");
        File dataRoot = new File(getDataFolder(), "plugin_data");
        pluginManager = new PluginManager(getLogger(), pluginsDir, dataRoot, eventBus, commandRegistry, Bukkit::broadcastMessage);
        pluginManager.loadAll();

        getLogger().info("HitBoy's Mixed Plugin Loader is up -- " + pluginManager.loadedPlugins().size()
            + " plugin(s) loaded from " + pluginsDir.getPath());
    }

    @Override
    public void onDisable() {
        if (pluginManager != null) {
            pluginManager.disableAll();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            pluginManager.disableAll();
            pluginManager.loadAll();
            sender.sendMessage("Reloaded " + pluginManager.loadedPlugins().size() + " HitBoy plugin(s).");
            return true;
        }
        sender.sendMessage("HitBoy's Mixed Plugin Loader -- " + pluginManager.loadedPlugins().size() + " plugin(s) loaded:");
        for (PluginManager.LoadedPlugin loaded : pluginManager.loadedPlugins()) {
            sender.sendMessage(" - " + loaded.descriptor().name() + " v" + loaded.descriptor().version()
                + " by " + loaded.descriptor().author());
        }
        return true;
    }
}
