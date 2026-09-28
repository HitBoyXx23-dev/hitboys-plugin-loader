package com.hitboy.pluginloader.core;

import com.hitboy.pluginloader.api.ScheduledTask;
import com.hitboy.pluginloader.api.Scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The scheduler behind {@link Scheduler}. The loader calls {@link #tick()} once per server tick on the
 * server thread (standalone: a hook in the server's tick; mixed: a Bukkit repeating task).
 */
public final class TickScheduler {
    private final Logger logger;
    private final List<Task> tasks = new ArrayList<>();
    private long currentTick;

    public TickScheduler(Logger logger) {
        this.logger = logger;
    }

    private static final class Task implements ScheduledTask {
        final Runnable body;
        final long period;
        final Object owner;
        long nextTick;
        volatile boolean cancelled;

        Task(Runnable body, long nextTick, long period, Object owner) {
            this.body = body;
            this.nextTick = nextTick;
            this.period = period;
            this.owner = owner;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    /** A scheduler for one plugin; its tasks are cancelled with {@link #cancelAll(Object)}. */
    public Scheduler forOwner(final Object owner) {
        return new Scheduler() {
            @Override
            public ScheduledTask runLater(Runnable task, long delayTicks) {
                return schedule(task, delayTicks, -1, owner);
            }

            @Override
            public ScheduledTask runRepeating(Runnable task, long delayTicks, long periodTicks) {
                if (periodTicks < 1) throw new IllegalArgumentException("periodTicks must be at least 1");
                return schedule(task, delayTicks, periodTicks, owner);
            }
        };
    }

    private synchronized ScheduledTask schedule(Runnable body, long delayTicks, long period, Object owner) {
        if (body == null) throw new IllegalArgumentException("task is null");
        Task task = new Task(body, currentTick + Math.max(0, delayTicks) + 1, period, owner);
        tasks.add(task);
        return task;
    }

    public synchronized void cancelAll(Object owner) {
        for (Task task : tasks) if (task.owner == owner) task.cancelled = true;
    }

    /** Runs every task that is due. Called once per server tick on the server thread. */
    public void tick() {
        List<Task> due = new ArrayList<>();
        synchronized (this) {
            currentTick++;
            tasks.removeIf(task -> task.cancelled);
            for (Task task : tasks) if (task.nextTick <= currentTick) due.add(task);
        }
        for (Task task : due) {
            if (task.cancelled) continue;
            try {
                task.body.run();
            } catch (Throwable failure) {
                logger.log(Level.WARNING, "A scheduled plugin task failed", failure);
            }
            synchronized (this) {
                if (task.period > 0) task.nextTick = currentTick + task.period;
                else task.cancelled = true;
            }
        }
    }
}
