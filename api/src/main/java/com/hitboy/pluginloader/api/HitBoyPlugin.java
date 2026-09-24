package com.hitboy.pluginloader.api;

/**
 * Every HitBoy plugin implements this instead of extending Bukkit's
 * {@code JavaPlugin} directly. It is loaded by {@code HitBoysPluginLoader}
 * from a jar containing a {@code plugin.json} descriptor, and receives a
 * {@link PluginContext} instead of raw Bukkit internals -- this keeps
 * plugin code stable even if the underlying server API changes.
 */
public interface HitBoyPlugin {

    /**
     * Called once, after the loader has read {@code plugin.json} and set up
     * this plugin's {@link PluginContext}. Register event listeners and
     * commands here.
     */
    void onEnable(PluginContext context);

    /**
     * Called when the loader is shutting down (server stop, or the loader's
     * {@code /hitboyplugins reload} command). Release any resources here.
     */
    default void onDisable() {
    }
}
