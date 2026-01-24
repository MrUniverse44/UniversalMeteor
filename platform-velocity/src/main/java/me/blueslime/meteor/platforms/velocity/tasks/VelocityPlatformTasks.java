package me.blueslime.meteor.platforms.velocity.tasks;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import me.blueslime.meteor.implementation.Implements;
import me.blueslime.meteor.platforms.api.tasks.DefaultPlatformTasks;
import me.blueslime.meteor.platforms.api.tasks.PlatformTasks;
import me.blueslime.meteor.platforms.api.tasks.handle.TaskHandle;
import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;
import me.blueslime.meteor.platforms.velocity.tasks.handle.VelocityTaskHandle;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Callable;

/**
 * VelocityPlatformTasks - integrates with Velocity's Scheduler when ProxyServer + Plugin provided.
 * Falls back to DefaultPlatformTasks if the proxy or plugin is null.
 */
public class VelocityPlatformTasks implements PlatformTasks {

    private final ProxyServer proxy;
    private final Object plugin;
    private final DefaultPlatformTasks fallback;
    private final Map<String, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<?>> futures = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(0);

    public VelocityPlatformTasks(ProxyServer proxy) {
        this.proxy = proxy;
        this.plugin = Implements.fetch(Object.class, "adapter");
        this.fallback = (proxy == null || plugin == null) ? new DefaultPlatformTasks() : null;
    }

    private String nextId() {
        return idCounter.incrementAndGet() + "-" + UUID.randomUUID();
    }

    @Override
    public TaskHandle schedule(Runnable task, TaskOptions options) {
        if (proxy == null || plugin == null) return fallback.schedule(task, options);

        final boolean repeating = options != null && options.getRepeatDelay() > 0;
        final long delay = options != null ? options.getDelay() : 0;
        final java.util.concurrent.TimeUnit unit = options != null ? options.getTimeUnit() : java.util.concurrent.TimeUnit.MILLISECONDS;
        final long period = options != null ? options.getRepeatDelay() : 0;

        String id = nextId();
        CompletableFuture<Void> future = new CompletableFuture<>();

        Runnable wrapped = () -> {
            try {
                task.run();
                if (!repeating) future.complete(null);
            } catch (Throwable t) {
                if (!repeating) future.completeExceptionally(t);
                throw t;
            }
        };

        Scheduler scheduler = proxy.getScheduler();

        ScheduledTask scheduled;
        if (repeating) {
            scheduled = scheduler.buildTask(plugin, wrapped)
                    .delay(delay, unit)
                    .repeat(period, unit)
                    .schedule();
        } else if (delay > 0) {
            scheduled = scheduler.buildTask(plugin, wrapped)
                    .delay(delay, unit)
                    .schedule();
        } else {
            scheduled = scheduler.buildTask(plugin, wrapped).schedule();
        }

        tasks.put(id, scheduled);
        futures.put(id, future);

        return new VelocityTaskHandle(id, scheduled, future, options);
    }

    @Override
    public <T> TaskHandle submit(Callable<T> callable, TaskOptions options) {
        if (proxy == null || plugin == null) return fallback.submit(callable, options);

        final boolean repeating = options != null && options.getRepeatDelay() > 0;
        final long delay = options != null ? options.getDelay() : 0;
        final java.util.concurrent.TimeUnit unit = options != null ? options.getTimeUnit() : java.util.concurrent.TimeUnit.MILLISECONDS;
        final long period = options != null ? options.getRepeatDelay() : 0;

        String id = nextId();
        CompletableFuture<T> cf = new CompletableFuture<>();

        Runnable runner = () -> {
            try {
                T res = callable.call();
                if (!repeating) cf.complete(res);
            } catch (Throwable t) {
                if (!repeating) cf.completeExceptionally(t);
                throw new RuntimeException(t);
            }
        };

        Scheduler scheduler = proxy.getScheduler();
        ScheduledTask scheduled;
        if (repeating) {
            scheduled = scheduler.buildTask(plugin, runner)
                    .delay(delay, unit)
                    .repeat(period, unit)
                    .schedule();
        } else if (delay > 0) {
            scheduled = scheduler.buildTask(plugin, runner)
                    .delay(delay, unit)
                    .schedule();
        } else {
            scheduled = scheduler.buildTask(plugin, runner).schedule();
        }

        tasks.put(id, scheduled);
        futures.put(id, cf);
        return new VelocityTaskHandle(id, scheduled, cf, options);
    }

    @Override
    public boolean isPrimaryThread() {
        // Velocity doesn't expose a Bukkit-like main thread concept. Return false.
        return false;
    }

    @Override
    public void cancelAll() {
        if (proxy == null || plugin == null) {
            fallback.cancelAll();
            return;
        }

        for (Map.Entry<String, ScheduledTask> e : tasks.entrySet()) {
            try {
                e.getValue().cancel();
            } catch (Throwable ignored) {}
        }
        tasks.clear();

        for (Map.Entry<String, CompletableFuture<?>> e : futures.entrySet()) {
            try {
                e.getValue().cancel(false);
            } catch (Throwable ignored) {}
        }
        futures.clear();
    }
}
