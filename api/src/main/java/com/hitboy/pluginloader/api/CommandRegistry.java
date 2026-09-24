package com.hitboy.pluginloader.api;

/**
 * Lets a {@link HitBoyPlugin} register its own commands. The standalone loader
 * adds them to the vanilla Brigadier command tree; the mixed loader adds them
 * to Bukkit's command map.
 */
public interface CommandRegistry {
    /** Registers a new command available to players and the console. */
    void register(String name, String description, CommandHandler handler);
}
