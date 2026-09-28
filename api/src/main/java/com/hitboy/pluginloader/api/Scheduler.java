package com.hitboy.pluginloader.api;

/**
 * Runs code on the server thread, counted in server ticks (20 ticks = 1 second). Tasks are cancelled
 * automatically when the plugin is disabled.
 */
public interface Scheduler {
    /** Runs {@code task} once, after {@code delayTicks} ticks (0 = next tick). */
    ScheduledTask runLater(Runnable task, long delayTicks);

    /** Runs {@code task} after {@code delayTicks} ticks, then every {@code periodTicks} ticks until cancelled. */
    ScheduledTask runRepeating(Runnable task, long delayTicks, long periodTicks);
}
