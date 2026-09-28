package com.hitboy.pluginloader.server;

import com.hitboy.pluginloader.api.PluginEventBus;
import com.hitboy.pluginloader.api.events.BlockBreakEvent;
import com.hitboy.pluginloader.api.events.BlockPlaceEvent;
import com.hitboy.pluginloader.api.events.PlayerDeathEvent;
import com.hitboy.pluginloader.core.TickScheduler;
import com.hitboy.pluginloader.api.events.PlayerChatEvent;
import com.hitboy.pluginloader.api.events.PlayerJoinEvent;
import com.hitboy.pluginloader.api.events.PlayerQuitEvent;
import com.hitboy.pluginloader.core.PluginManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Static entry points called from patched vanilla server code (see {@link ServerTransformer}).
 * Every method catches its own failures so a plugin or hook problem never takes the server down.
 */
public final class HitBoyServerHooks {
    private static final Logger LOGGER = Logger.getLogger("HitBoysPluginLoader");
    private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();

    private static VanillaBridge bridge;
    private static PluginEventBus events;
    private static PluginManager plugins;
    private static TickScheduler scheduler;
    private static boolean stopped;
    private static Object startingServer;

    private HitBoyServerHooks() {
    }

    private enum Kind { JOIN, QUIT, DEATH }

    /** A join, quit, or death whose vanilla system message has not been broadcast yet. */
    private static final class Pending {
        final Object player;
        final Kind kind;
        boolean fired;

        Pending(Object player, Kind kind) {
            this.player = player;
            this.kind = kind;
        }
    }

    public static void serverStarting(Object server) {
        startingServer = server;
    }

    public static void serverStarted() {
        try {
            Object server = startingServer;
            if (plugins != null || server == null) return;
            bridge = new VanillaBridge(ServerRuntime.mappings(), server);
            events = new PluginEventBus();
            File pluginsDirectory = new File("hitboy-plugins");
            scheduler = new TickScheduler(LOGGER);
            plugins = new PluginManager(LOGGER, pluginsDirectory, pluginsDirectory, events, bridge.commands(), bridge, scheduler);
            bridge.commands().registerBuiltIn(plugins);
            plugins.loadAll();
            try {
                bridge.failResult();
            } catch (Throwable unavailable) {
                LOGGER.warning("Plugins cannot cancel block placement on this Minecraft version: " + unavailable.getMessage());
            }
            List<String> missing = ServerRuntime.transformer().missingHooks();
            if (!missing.isEmpty()) {
                LOGGER.warning("Some HitBoy hooks did not apply on Minecraft " + ServerRuntime.mappings().version() + ": " + missing);
            }
            LOGGER.info("HitBoy's Plugin Loader is ready: " + plugins.loadedPlugins().size()
                + " plugin(s) loaded from " + pluginsDirectory.getPath());
        } catch (Throwable failure) {
            report("start plugins", failure);
        }
    }

    public static void serverStopping(Object server) {
        try {
            if (plugins == null || stopped) return;
            stopped = true;
            plugins.disableAll();
        } catch (Throwable failure) {
            report("stop plugins", failure);
        }
    }

    public static void commandsCreated(Object commands) {
        try {
            VanillaCommands.onCommandsCreated(commands);
        } catch (Throwable failure) {
            report("register commands", failure);
        }
    }

    public static void beginJoin(Object player) {
        PENDING.set(new Pending(player, Kind.JOIN));
    }

    public static void endJoin() {
        Pending pending = PENDING.get();
        PENDING.remove();
        if (pending != null && !pending.fired) fire(pending, null);
    }

    public static void beginQuit(Object listener) {
        try {
            if (bridge != null) PENDING.set(new Pending(bridge.playerOf(listener), Kind.QUIT));
        } catch (Throwable failure) {
            report("track quit", failure);
        }
    }

    public static void endQuit() {
        endJoin();
    }

    public static void beginDeath(Object player) {
        if (bridge != null) PENDING.set(new Pending(player, Kind.DEATH));
    }

    public static void endDeath() {
        endJoin();
    }

    /** Called at the start of every server tick: runs scheduled plugin tasks. */
    public static void tick() {
        TickScheduler current = scheduler;
        if (current == null || stopped) return;
        try {
            current.tick();
        } catch (Throwable failure) {
            report("run scheduled tasks", failure);
        }
    }

    /** Returns InteractionResult.FAIL when a plugin cancelled the placement, else null (place normally). */
    public static Object blockPlace(Object blockItem, Object context) {
        if (bridge == null) return null;
        try {
            Object player = bridge.contextPlayer(context);
            if (player == null) return null; // dispensers and other non-player placements
            Object level = bridge.contextLevel(context);
            int[] xyz = bridge.coordinates(bridge.contextPosition(context));
            BlockPlaceEvent event = new BlockPlaceEvent(bridge.playerName(player), bridge.playerId(player),
                bridge.worldName(level), xyz[0], xyz[1], xyz[2], bridge.blockOfItem(blockItem));
            events.publish(event);
            return event.isCancelled() ? bridge.failResult() : null;
        } catch (Throwable failure) {
            report("handle block place", failure);
            return null;
        }
    }

    /** Called for every system broadcast; returns the component to send, or null to send nothing. */
    public static Object systemMessage(Object playerList, Object component) {
        Pending pending = PENDING.get();
        if (pending == null || pending.fired || bridge == null) return component;
        try {
            String original = bridge.text(component);
            String replacement = fire(pending, original);
            if (replacement == null) return null;
            return replacement.equals(original) ? component : bridge.literal(replacement);
        } catch (Throwable failure) {
            report("handle join/quit message", failure);
            return component;
        }
    }

    /** Returns true when the chat message was cancelled or replaced by a plugin. */
    public static boolean chat(Object listener, Object message) {
        if (bridge == null) return false;
        try {
            Object player = bridge.playerOf(listener);
            String original = bridge.chatText(message);
            PlayerChatEvent event = new PlayerChatEvent(bridge.playerName(player), bridge.playerId(player), original);
            events.publish(event);
            if (event.isCancelled()) return true;
            if (!original.equals(event.message())) {
                bridge.broadcast("<" + event.playerName() + "> " + event.message());
                return true;
            }
            return false;
        } catch (Throwable failure) {
            report("handle chat", failure);
            return false;
        }
    }

    /** Returns true when a plugin cancelled the block break. */
    public static boolean blockBreak(Object gameMode, Object position) {
        if (bridge == null) return false;
        try {
            Object player = bridge.playerOf(gameMode);
            Object level = bridge.levelOf(gameMode);
            int[] xyz = bridge.coordinates(position);
            BlockBreakEvent event = new BlockBreakEvent(bridge.playerName(player), bridge.playerId(player),
                bridge.worldName(level), xyz[0], xyz[1], xyz[2], bridge.blockType(level, position));
            events.publish(event);
            return event.isCancelled();
        } catch (Throwable failure) {
            report("handle block break", failure);
            return false;
        }
    }

    private static String fire(Pending pending, String message) {
        pending.fired = true;
        if (bridge == null || pending.player == null) return message;
        String name = bridge.playerName(pending.player);
        UUID id = bridge.playerId(pending.player);
        if (pending.kind == Kind.JOIN) {
            PlayerJoinEvent event = new PlayerJoinEvent(name, id, message);
            events.publish(event);
            return event.joinMessage();
        }
        if (pending.kind == Kind.DEATH) {
            PlayerDeathEvent event = new PlayerDeathEvent(name, id, message);
            events.publish(event);
            return event.deathMessage();
        }
        PlayerQuitEvent event = new PlayerQuitEvent(name, id, message);
        events.publish(event);
        return event.quitMessage();
    }

    static void log(String message) {
        LOGGER.info(message);
    }

    private static void report(String action, Throwable failure) {
        LOGGER.log(java.util.logging.Level.WARNING, "HitBoy could not " + action, failure);
    }

    static void configureLogging() {
        Logger root = Logger.getLogger("");
        for (Handler handler : root.getHandlers()) root.removeHandler(handler);
        ConsoleHandler handler = new ConsoleHandler() {
            {
                setOutputStream(System.out);
            }
        };
        handler.setFormatter(new Formatter() {
            private final SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss");

            @Override
            public String format(LogRecord record) {
                String line = "[" + time.format(new Date(record.getMillis())) + "] [HitBoy/" + record.getLevel() + "] ["
                    + record.getLoggerName() + "]: " + formatMessage(record) + System.lineSeparator();
                if (record.getThrown() != null) {
                    java.io.StringWriter trace = new java.io.StringWriter();
                    record.getThrown().printStackTrace(new java.io.PrintWriter(trace));
                    line += trace;
                }
                return line;
            }
        });
        root.addHandler(handler);
    }
}
