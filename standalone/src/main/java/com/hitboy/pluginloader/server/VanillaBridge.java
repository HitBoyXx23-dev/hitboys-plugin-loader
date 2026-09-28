package com.hitboy.pluginloader.server;

import com.hitboy.pluginloader.api.PlayerHandle;
import com.hitboy.pluginloader.api.ServerAccess;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads and drives vanilla server objects through reflection. All member names are written in
 * Mojang's official names and translated by {@link Mappings}, so the same code runs on obfuscated
 * (1.21.x) and unobfuscated (26.x) servers.
 */
final class VanillaBridge implements ServerAccess {
    private static final String COMPONENT = "net.minecraft.network.chat.Component";

    private final Mappings mappings;
    private final Object server;
    private final ClassLoader loader;
    private final VanillaCommands commands;
    private final Map<String, Method> methods = new ConcurrentHashMap<>();
    private final Map<String, Field> fields = new ConcurrentHashMap<>();

    VanillaBridge(Mappings mappings, Object server) {
        this.mappings = mappings;
        this.server = server;
        this.loader = server.getClass().getClassLoader();
        this.commands = new VanillaCommands(this);
    }

    VanillaCommands commands() {
        return commands;
    }

    Mappings mappings() {
        return mappings;
    }

    @Override
    public void broadcast(String message) {
        Object playerList = call(server, "net.minecraft.server.MinecraftServer", "getPlayerList", "");
        call(playerList, "net.minecraft.server.players.PlayerList", "broadcastSystemMessage",
            COMPONENT + ",boolean", literal(message), false);
    }

    @Override
    public java.util.List<PlayerHandle> onlinePlayers() {
        Object playerList = call(server, "net.minecraft.server.MinecraftServer", "getPlayerList", "");
        java.util.List<PlayerHandle> players = new java.util.ArrayList<>();
        for (Object player : (java.util.List<?>) call(playerList, "net.minecraft.server.players.PlayerList", "getPlayers", "")) {
            players.add(new VanillaPlayer(this, player));
        }
        return players;
    }

    @Override
    public PlayerHandle player(String name) {
        for (PlayerHandle player : onlinePlayers()) if (player.name().equalsIgnoreCase(name)) return player;
        return null;
    }

    @Override
    public void runCommand(String command) {
        Object commands = call(server, "net.minecraft.server.MinecraftServer", "getCommands", "");
        Object source = call(server, "net.minecraft.server.MinecraftServer", "createCommandSourceStack", "");
        call(commands, "net.minecraft.commands.Commands", "performPrefixedCommand",
            "net.minecraft.commands.CommandSourceStack,java.lang.String", source, command);
    }

    /** True when the player is a server operator (in the op list). */
    boolean isOp(Object player) {
        try {
            Object profile = gameProfile(player);
            Class<?> nameAndId = runtimeClass("net.minecraft.server.players.NameAndId");
            Object key = nameAndId.getConstructor(profile.getClass()).newInstance(profile);
            Object playerList = call(server, "net.minecraft.server.MinecraftServer", "getPlayerList", "");
            return (Boolean) call(playerList, "net.minecraft.server.players.PlayerList", "isOp", "net.minecraft.server.players.NameAndId", key);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not check operator status", failure);
        }
    }

    Object connectionOf(Object player) {
        return fieldOfType(player, "net.minecraft.server.network.ServerGamePacketListenerImpl");
    }

    Object contextPlayer(Object context) {
        return call(context, "net.minecraft.world.item.context.UseOnContext", "getPlayer", "");
    }

    Object contextLevel(Object context) {
        return call(context, "net.minecraft.world.item.context.UseOnContext", "getLevel", "");
    }

    Object contextPosition(Object context) {
        return call(context, "net.minecraft.world.item.context.UseOnContext", "getClickedPos", "");
    }

    /** Block id of a block item, such as {@code minecraft:stone}. */
    String blockOfItem(Object blockItem) {
        String text = String.valueOf(call(blockItem, "net.minecraft.world.item.BlockItem", "getBlock", "")); // "Block{minecraft:stone}"
        int open = text.indexOf('{');
        int close = text.indexOf('}');
        return open >= 0 && close > open ? text.substring(open + 1, close) : text;
    }

    /** {@code InteractionResult.FAIL}. */
    Object failResult() {
        try {
            String owner = "net.minecraft.world.InteractionResult";
            Field fail = runtimeClass(owner).getField(mappings.fieldName(owner, "FAIL"));
            return fail.get(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not read InteractionResult.FAIL", failure);
        }
    }

    Object literal(String text) {
        return callStatic(COMPONENT, "literal", "java.lang.String", text);
    }

    String text(Object component) {
        return (String) call(component, COMPONENT, "getString", "");
    }

    String chatText(Object playerChatMessage) {
        return (String) call(playerChatMessage, "net.minecraft.network.chat.PlayerChatMessage", "signedContent", "");
    }

    Object playerOf(Object holder) {
        return fieldOfType(holder, "net.minecraft.server.level.ServerPlayer");
    }

    Object levelOf(Object holder) {
        return fieldOfType(holder, "net.minecraft.server.level.ServerLevel");
    }

    String playerName(Object player) {
        return (String) profileValue(player, "name", "getName");
    }

    UUID playerId(Object player) {
        return (UUID) profileValue(player, "id", "getId");
    }

    int[] coordinates(Object blockPos) {
        String vec = "net.minecraft.core.Vec3i";
        return new int[] {
            (Integer) call(blockPos, vec, "getX", ""),
            (Integer) call(blockPos, vec, "getY", ""),
            (Integer) call(blockPos, vec, "getZ", "")
        };
    }

    /** Block id such as {@code minecraft:stone}. */
    String blockType(Object level, Object blockPos) {
        Object state = call(level, "net.minecraft.world.level.Level", "getBlockState", "net.minecraft.core.BlockPos", blockPos);
        String text = String.valueOf(state); // "Block{minecraft:stone}[...]"
        int open = text.indexOf('{');
        int close = text.indexOf('}');
        return open >= 0 && close > open ? text.substring(open + 1, close) : text;
    }

    /** Dimension id such as {@code minecraft:overworld}. */
    String worldName(Object level) {
        String key = String.valueOf(call(level, "net.minecraft.world.level.Level", "dimension", "")); // "ResourceKey[minecraft:dimension / minecraft:overworld]"
        int slash = key.lastIndexOf(" / ");
        return slash >= 0 ? key.substring(slash + 3, key.length() - 1) : key;
    }

    Object call(Object target, String owner, String name, String parameters, Object... arguments) {
        try {
            return method(target.getClass(), owner, name, parameters).invoke(target, arguments);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not call " + owner + "#" + name, failure);
        }
    }

    Object callStatic(String owner, String name, String parameters, Object... arguments) {
        try {
            return method(runtimeClass(owner), owner, name, parameters).invoke(null, arguments);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not call " + owner + "#" + name, failure);
        }
    }

    Class<?> runtimeClass(String mojangName) throws ClassNotFoundException {
        return Class.forName(mappings.className(mojangName), false, loader);
    }

    private Method method(Class<?> start, String owner, String name, String parameters) throws ReflectiveOperationException {
        String key = start.getName() + "#" + owner + "#" + name + "(" + parameters + ")";
        Method cached = methods.get(key);
        if (cached != null) return cached;
        String runtimeName = mappings.methodName(owner, name, parameters);
        Class<?>[] types = parameterTypes(parameters);
        for (Class<?> type = start; type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(runtimeName, types);
                method.setAccessible(true);
                methods.put(key, method);
                return method;
            } catch (NoSuchMethodException ignored) {
                // keep walking up
            }
        }
        Method method = runtimeClass(owner).getMethod(runtimeName, types); // interface methods such as Component#getString
        method.setAccessible(true);
        methods.put(key, method);
        return method;
    }

    private Class<?>[] parameterTypes(String parameters) throws ClassNotFoundException {
        if (parameters.isEmpty()) return new Class<?>[0];
        String[] names = parameters.split(",");
        Class<?>[] types = new Class<?>[names.length];
        for (int index = 0; index < names.length; index++) {
            switch (names[index]) {
                case "boolean": types[index] = boolean.class; break;
                case "int": types[index] = int.class; break;
                case "long": types[index] = long.class; break;
                case "float": types[index] = float.class; break;
                case "double": types[index] = double.class; break;
                default: types[index] = names[index].startsWith("java.")
                    ? Class.forName(names[index]) : runtimeClass(names[index]);
            }
        }
        return types;
    }

    private Object fieldOfType(Object holder, String mojangType) {
        String key = holder.getClass().getName() + "#" + mojangType;
        try {
            Field field = fields.get(key);
            if (field == null) {
                Class<?> wanted = runtimeClass(mojangType);
                search:
                for (Class<?> type = holder.getClass(); type != null; type = type.getSuperclass()) {
                    for (Field candidate : type.getDeclaredFields()) {
                        if (!java.lang.reflect.Modifier.isStatic(candidate.getModifiers()) && candidate.getType() == wanted) {
                            field = candidate;
                            break search;
                        }
                    }
                }
                if (field == null) throw new IllegalStateException("No " + mojangType + " field on " + holder.getClass().getName());
                field.setAccessible(true);
                fields.put(key, field);
            }
            return field.get(holder);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not read " + mojangType + " from " + holder.getClass().getName(), failure);
        }
    }

    /** The player's authlib GameProfile. */
    private Object gameProfile(Object player) throws ReflectiveOperationException {
        for (Class<?> type = player.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType().getName().equals("com.mojang.authlib.GameProfile")) {
                    method.setAccessible(true);
                    return method.invoke(player);
                }
            }
        }
        throw new IllegalStateException("No GameProfile on " + player.getClass().getName());
    }

    /** Reads a value from the player's authlib GameProfile (a record on new versions, a class on old ones). */
    private Object profileValue(Object player, String recordAccessor, String getter) {
        try {
            Object profile = gameProfile(player);
            Method accessor;
            try {
                accessor = profile.getClass().getMethod(recordAccessor);
            } catch (NoSuchMethodException oldAuthlib) {
                accessor = profile.getClass().getMethod(getter);
            }
            return accessor.invoke(profile);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not read the player's profile", failure);
        }
    }
}
