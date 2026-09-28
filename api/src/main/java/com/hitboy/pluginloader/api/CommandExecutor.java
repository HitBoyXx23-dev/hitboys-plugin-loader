package com.hitboy.pluginloader.api;

/**
 * Runs a command registered with {@link CommandRegistry#registerCommand}. Return false to show a
 * usage error to the sender.
 */
public interface CommandExecutor {
    boolean execute(CommandSender sender, String[] args);
}
