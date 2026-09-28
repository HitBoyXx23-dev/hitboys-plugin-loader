package com.hitboy.pluginloader.api;

/**
 * Lets a {@link HitBoyPlugin} register its own commands. The standalone loader
 * adds them to the vanilla Brigadier command tree; the mixed loader adds them
 * to Bukkit's command map.
 */
public interface CommandRegistry {
    /** Registers a new command available to players and the console. */
    void register(String name, String description, CommandHandler handler);

    /**
     * Registers a command whose executor gets the {@link CommandSender}, so it can reply to the sender,
     * check whether it is a player or an operator, and reach the player.
     */
    void registerCommand(String name, String description, CommandExecutor executor);
}
