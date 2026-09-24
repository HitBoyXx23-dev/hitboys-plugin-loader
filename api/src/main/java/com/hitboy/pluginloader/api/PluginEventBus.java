package com.hitboy.pluginloader.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A typed pub/sub bus for HitBoy plugin events. Each loader translates its
 * server's events into these simplified types once (the standalone loader from
 * patched vanilla server code, the mixed loader from Bukkit events) and fans
 * them out here, so plugins never depend on server internals.
 */
public final class PluginEventBus {
    private static final Logger LOGGER = Logger.getLogger("HitBoysPluginLoader");

    private final Map<Class<?>, List<Consumer<Object>>> handlers = new ConcurrentHashMap<>();

    /** Registers a handler for a specific event type, e.g. {@code PlayerJoinEvent.class}. */
    @SuppressWarnings("unchecked")
    public <T> void subscribe(Class<T> eventType, Consumer<T> handler) {
        handlers
            .computeIfAbsent(eventType, key -> new CopyOnWriteArrayList<>())
            .add((Consumer<Object>) handler);
    }

    /** Dispatches an event instance to every handler subscribed to its exact class. */
    public void publish(Object event) {
        List<Consumer<Object>> subscribed = handlers.get(event.getClass());
        if (subscribed == null || subscribed.isEmpty()) {
            return;
        }
        for (Consumer<Object> handler : subscribed) {
            try {
                handler.accept(event);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "A HitBoy plugin handler threw an exception for "
                    + event.getClass().getSimpleName(), e);
            }
        }
    }
}
