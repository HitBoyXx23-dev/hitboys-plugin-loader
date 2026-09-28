package com.hitboy.pluginloader.api;

/** A task returned by {@link Scheduler}. */
public interface ScheduledTask {
    void cancel();

    boolean isCancelled();
}
