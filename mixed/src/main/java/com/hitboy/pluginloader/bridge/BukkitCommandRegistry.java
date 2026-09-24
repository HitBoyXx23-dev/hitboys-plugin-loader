package com.hitboy.pluginloader.bridge;

import com.hitboy.pluginloader.api.CommandHandler;
import com.hitboy.pluginloader.api.CommandRegistry;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registers commands directly with Bukkit's {@code CommandMap} via
 * reflection, so a HitBoy plugin can call {@code commands().register(...)}
 * without needing a {@code plugin.yml} command block or its own
 * {@code JavaPlugin} instance. The reflected {@code CommandMap} field on
 * {@code Bukkit.getServer()} has been stable public-facing behavior since
 * Bukkit 1.8, which is why this approach works across the wide version
 * range Spigot/Paper/Purpur servers can run.
 */
public final class BukkitCommandRegistry implements CommandRegistry {
    private final Plugin ownerPlugin;
    private final Logger logger;
    private final CommandMap commandMap;

    public BukkitCommandRegistry(Plugin ownerPlugin, Logger logger) {
        this.ownerPlugin = ownerPlugin;
        this.logger = logger;
        this.commandMap = resolveCommandMap();
    }

    private CommandMap resolveCommandMap() {
        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            return (CommandMap) field.get(Bukkit.getServer());
        } catch (ReflectiveOperationException e) {
            logger.log(Level.SEVERE, "Could not access Bukkit's CommandMap -- custom commands will not work.", e);
            return null;
        }
    }

    @Override
    public void register(String name, String description, CommandHandler handler) {
        if (commandMap == null) {
            logger.warning("Cannot register command /" + name + " -- CommandMap unavailable.");
            return;
        }
        Command command = new Command(name, description, "/" + name, Collections.<String>emptyList()) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                try {
                    if (!handler.execute(sender.getName(), args)) {
                        sender.sendMessage("Usage: " + getUsage());
                    }
                } catch (RuntimeException e) {
                    sender.sendMessage("An internal error occurred running /" + name + ".");
                    logger.log(Level.WARNING, "Error executing /" + name, e);
                }
                return true;
            }
        };
        commandMap.register(ownerPlugin.getName().toLowerCase(), command);
    }
}
