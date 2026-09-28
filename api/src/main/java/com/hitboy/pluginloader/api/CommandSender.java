package com.hitboy.pluginloader.api;

/** Whoever ran a command: a player or the server console. */
public interface CommandSender {
    String name();

    /** Sends a message to this sender only (the player's chat, or the console log). */
    void sendMessage(String message);

    boolean isPlayer();

    /** True for the console and for server operators. */
    boolean isOp();

    /** The player who ran the command, or null for the console. */
    PlayerHandle player();
}
