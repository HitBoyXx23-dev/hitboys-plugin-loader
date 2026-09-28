package com.hitboy.pluginloader.api;

/**
 * Server actions a {@link HitBoyPlugin} can perform without knowing whether it
 * runs on the standalone HitBoy loader (vanilla server.jar) or the mixed loader
 * (Spigot/Paper/Purpur).
 */
public interface ServerAccess {
    /** Sends a system message to every online player and the console. */
    void broadcast(String message);

    /** Every player online right now. */
    java.util.List<PlayerHandle> onlinePlayers();

    /** The online player with this name (ignoring case), or null. */
    PlayerHandle player(String name);

    /** Runs a command as the server console, for example {@code "time set day"} (no leading slash). */
    void runCommand(String command);
}
