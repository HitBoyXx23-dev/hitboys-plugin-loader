package com.hitboy.pluginloader.api;

import java.io.File;
import java.util.logging.Logger;

/**
 * The handle a {@link HitBoyPlugin} is given on enable. It wraps everything a
 * plugin should need without exposing server internals (vanilla or Bukkit), so plugin
 * authors write against one small, stable surface rather than the full
 * Bukkit/Paper API (which does change between major Minecraft versions).
 */
public final class PluginContext {
    private final String pluginName;
    private final String pluginVersion;
    private final File dataFolder;
    private final Logger logger;
    private final PluginEventBus eventBus;
    private final CommandRegistry commandRegistry;
    private final ServerAccess server;
    private final Scheduler scheduler;

    public PluginContext(
        String pluginName,
        String pluginVersion,
        File dataFolder,
        Logger logger,
        PluginEventBus eventBus,
        CommandRegistry commandRegistry,
        ServerAccess server
    ) {
        this(pluginName, pluginVersion, dataFolder, logger, eventBus, commandRegistry, server, null);
    }

    public PluginContext(
        String pluginName,
        String pluginVersion,
        File dataFolder,
        Logger logger,
        PluginEventBus eventBus,
        CommandRegistry commandRegistry,
        ServerAccess server,
        Scheduler scheduler
    ) {
        this.pluginName = pluginName;
        this.pluginVersion = pluginVersion;
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.eventBus = eventBus;
        this.commandRegistry = commandRegistry;
        this.server = server;
        this.scheduler = scheduler;
    }

    public String pluginName() {
        return pluginName;
    }

    public String pluginVersion() {
        return pluginVersion;
    }

    /** A per-plugin data folder, created on demand, e.g. for config files. */
    public File dataFolder() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        return dataFolder;
    }

    public Logger logger() {
        return logger;
    }

    /** The custom, server-version-agnostic event bus (join/chat/command/block events). */
    public PluginEventBus events() {
        return eventBus;
    }

    /** Registers commands without touching Bukkit's CommandMap directly. */
    public CommandRegistry commands() {
        return commandRegistry;
    }

    /** Server actions: broadcasting, online players, running console commands. */
    public ServerAccess server() {
        return server;
    }

    /** Runs code later or repeatedly on the server thread; tasks stop when the plugin is disabled. */
    public Scheduler scheduler() {
        if (scheduler == null) throw new IllegalStateException("This loader does not provide a scheduler");
        return scheduler;
    }
}
