package com.hitboy.pluginloader.api;

import java.util.UUID;

/** An online player. Only valid while the player is online. */
public interface PlayerHandle {
    String name();

    UUID id();

    void sendMessage(String message);

    /** World id: a dimension id such as {@code minecraft:overworld} (standalone) or the world's name (Bukkit). */
    String worldName();

    double x();

    double y();

    double z();

    boolean isOp();

    /** Disconnects the player with the given reason. */
    void kick(String reason);
}
