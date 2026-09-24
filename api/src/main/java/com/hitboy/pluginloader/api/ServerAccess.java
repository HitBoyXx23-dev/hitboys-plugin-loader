package com.hitboy.pluginloader.api;

/**
 * Server actions a {@link HitBoyPlugin} can perform without knowing whether it
 * runs on the standalone HitBoy loader (vanilla server.jar) or the mixed loader
 * (Spigot/Paper/Purpur).
 */
public interface ServerAccess {
    /** Sends a system message to every online player and the console. */
    void broadcast(String message);
}
