package com.hitboy.pluginloader.server;

import com.hitboy.pluginloader.api.CommandExecutor;
import com.hitboy.pluginloader.api.CommandHandler;
import com.hitboy.pluginloader.api.CommandRegistry;
import com.hitboy.pluginloader.api.CommandSender;
import com.hitboy.pluginloader.api.PlayerHandle;
import com.hitboy.pluginloader.core.PluginManager;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adds HitBoy plugin commands to vanilla's Brigadier command tree. Vanilla rebuilds that tree on
 * {@code /reload}, so every registered command is re-added whenever a new {@code Commands} instance
 * is created.
 */
final class VanillaCommands implements CommandRegistry {
    private static final Logger LOGGER = Logger.getLogger("HitBoysPluginLoader");
    private static final String COMMANDS = "net.minecraft.commands.Commands";
    private static final String SOURCE = "net.minecraft.commands.CommandSourceStack";

    private static final List<Registered> REGISTERED = new ArrayList<>();
    private static Object latestCommands;
    private static VanillaCommands active;

    private final VanillaBridge bridge;

    private static final class Registered {
        final String name;
        final CommandExecutor executor;

        Registered(String name, CommandExecutor executor) {
            this.name = name;
            this.executor = executor;
        }
    }

    VanillaCommands(VanillaBridge bridge) {
        this.bridge = bridge;
        active = this;
    }

    static synchronized void onCommandsCreated(Object commands) {
        latestCommands = commands;
        if (active == null) return;
        for (Registered command : REGISTERED) active.addToTree(commands, command);
    }

    @Override
    public void register(String name, String description, CommandHandler handler) {
        registerCommand(name, description, (sender, arguments) -> handler.execute(sender.name(), arguments));
    }

    @Override
    public synchronized void registerCommand(String name, String description, CommandExecutor executor) {
        Registered command = new Registered(name.toLowerCase(java.util.Locale.ROOT), executor);
        REGISTERED.add(command);
        if (latestCommands != null) addToTree(latestCommands, command);
    }

    void registerBuiltIn(PluginManager plugins) {
        CommandExecutor list = (sender, arguments) -> {
            StringBuilder message = new StringBuilder("HitBoy plugins (" + plugins.loadedPlugins().size() + "):");
            for (PluginManager.LoadedPlugin plugin : plugins.loadedPlugins()) {
                message.append(' ').append(plugin.descriptor().name()).append(" v").append(plugin.descriptor().version());
            }
            sender.sendMessage(message.toString());
            return true;
        };
        registerCommand("hitboyplugins", "Lists HitBoy plugins.", list);
        registerCommand("hbp", "Lists HitBoy plugins.", list);
    }

    private void addToTree(Object commands, Registered command) {
        try {
            Object dispatcher = bridge.call(commands, COMMANDS, "getDispatcher", "");
            ClassLoader loader = dispatcher.getClass().getClassLoader();
            Class<?> literalBuilder = Class.forName("com.mojang.brigadier.builder.LiteralArgumentBuilder", true, loader);
            Class<?> requiredBuilder = Class.forName("com.mojang.brigadier.builder.RequiredArgumentBuilder", true, loader);
            Class<?> argumentBuilder = Class.forName("com.mojang.brigadier.builder.ArgumentBuilder", true, loader);
            Class<?> argumentType = Class.forName("com.mojang.brigadier.arguments.ArgumentType", true, loader);
            Class<?> stringArgument = Class.forName("com.mojang.brigadier.arguments.StringArgumentType", true, loader);
            Class<?> brigadierCommand = Class.forName("com.mojang.brigadier.Command", true, loader);

            Object root = dispatcher.getClass().getMethod("getRoot").invoke(dispatcher);
            String name = command.name;
            Object existing = root.getClass().getMethod("getChild", String.class).invoke(root, name);
            if (existing != null && !isOurs(existing)) {
                name = "hitboy:" + name;
                LOGGER.warning("/" + command.name + " already exists in vanilla; registered HitBoy's version as /" + name);
            }

            Object executor = Proxy.newProxyInstance(loader, new Class<?>[] {brigadierCommand},
                (proxy, method, arguments) -> {
                    if (!"run".equals(method.getName())) return method.invoke(this, arguments);
                    return run(command, arguments[0]);
                });
            Method executes = argumentBuilder.getMethod("executes", brigadierCommand);
            Method then = argumentBuilder.getMethod("then", argumentBuilder);

            Object literal = literalBuilder.getMethod("literal", String.class).invoke(null, name);
            executes.invoke(literal, executor);
            Object greedy = stringArgument.getMethod("greedyString").invoke(null);
            Object argument = requiredBuilder.getMethod("argument", String.class, argumentType).invoke(null, "args", greedy);
            executes.invoke(argument, executor);
            then.invoke(literal, argument);
            dispatcher.getClass().getMethod("register", literalBuilder).invoke(dispatcher, literal);
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, "Could not register /" + command.name, failure);
        }
    }

    private static boolean isOurs(Object node) {
        try {
            Object executor = node.getClass().getMethod("getCommand").invoke(node);
            return executor != null && Proxy.isProxyClass(executor.getClass());
        } catch (ReflectiveOperationException failure) {
            return false;
        }
    }

    private int run(Registered command, Object context) {
        Object source = null;
        try {
            source = context.getClass().getMethod("getSource").invoke(context);
            CommandSender sender = new Sender(source);
            String[] arguments = new String[0];
            try {
                String raw = (String) context.getClass().getMethod("getArgument", String.class, Class.class)
                    .invoke(context, "args", String.class);
                arguments = raw.trim().isEmpty() ? new String[0] : raw.trim().split("\\s+");
            } catch (java.lang.reflect.InvocationTargetException noArguments) {
                // the command was run without arguments
            }
            if (command.executor.execute(sender, arguments)) return 1;
            fail(source, "Incorrect usage of /" + command.name);
            return 0;
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, "Error running /" + command.name, failure);
            if (source != null) fail(source, "An internal error occurred running /" + command.name);
            return 0;
        }
    }

    /** A command source (player, console, command block) as a {@link CommandSender}. */
    private final class Sender implements CommandSender {
        private final Object source;

        Sender(Object source) {
            this.source = source;
        }

        @Override
        public String name() {
            return (String) bridge.call(source, SOURCE, "getTextName", "");
        }

        @Override
        public void sendMessage(String message) {
            bridge.call(source, SOURCE, "sendSystemMessage", "net.minecraft.network.chat.Component", bridge.literal(message));
        }

        @Override
        public boolean isPlayer() {
            return player() != null;
        }

        @Override
        public boolean isOp() {
            PlayerHandle player = player();
            return player == null || player.isOp();
        }

        @Override
        public PlayerHandle player() {
            Object player = bridge.call(source, SOURCE, "getPlayer", "");
            return player == null ? null : new VanillaPlayer(bridge, player);
        }
    }

    private void fail(Object source, String message) {
        try {
            bridge.call(source, SOURCE, "sendFailure", "net.minecraft.network.chat.Component", bridge.literal(message));
        } catch (Throwable ignored) {
            LOGGER.warning(message);
        }
    }
}
