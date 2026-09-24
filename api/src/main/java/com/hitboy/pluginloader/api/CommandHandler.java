package com.hitboy.pluginloader.api;

/** A single command implementation registered through {@link CommandRegistry}. */
public interface CommandHandler {
    /**
     * @param senderName the command sender's display name (player or "CONSOLE")
     * @param args       arguments after the command label
     * @return true if the command was handled successfully; false tells the sender
     *         the command was used incorrectly
     */
    boolean execute(String senderName, String[] args);
}
