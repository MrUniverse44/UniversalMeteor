package me.blueslime.meteor.platforms.bungeecord.tasks;

import me.blueslime.meteor.platforms.api.tasks.DefaultPlatformTasks;
import me.blueslime.meteor.platforms.api.tasks.PlatformTasks;
import me.blueslime.meteor.platforms.api.tasks.handle.TaskHandle;
import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;
import me.blueslime.meteor.platforms.bungeecord.tasks.handle.BungeeTaskHandle;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class BungeePlatformTasks implements PlatformTasks {

    private final Plugin plugin;
    private final DefaultPlatformTasks fallback;
    private final Map<String, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<?>> futures = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(0);

    public BungeePlatformTasks(Plugin plugin) {
        this.plugin = plugin;
        this.fallback = plugin == null ? new DefaultPlatformTasks() : null;
    }

    private String nextId() {
        return Long.toString(idCounter.incrementAndGet()) + "-" + UUID.randomUUID().toString();
    }

    private long toTimeUnitMillis(long value, java.util.concurrent.TimeUnit unit) {
        return unit == null ? value : unit.toMillis(value);
    }

    @Override
    public TaskHandle schedule(Runnable task, TaskOptions options) {
        if (plugin == null) return fallback.schedule(task, options);

        final boolean repeating = options != null && options.getRepeatDelay() > 0;
        final long delay = options != null ? options.getDelay() : 0;
        final java.util.concurrent.TimeUnit unit = options != null ? options.getTimeUnit() : java.util.concurrent.TimeUnit.MILLISECONDS;
        final long period = options != null ? options.getRepeatDelay() : 0;

        String id = nextId();
        CompletableFuture<Void> future = new CompletableFuture<>();
        Runnable wrapped = () -> {
            try {
                task.run();
                // complete only for non-repeating tasks
                if (!repeating) future.complete(null);
            } catch (Throwable t) {
                if (!repeating) future.completeExceptionally(t);
                throw t;
            }
        };

        ScheduledTask scheduled;
        if (repeating) {
            scheduled = plugin.getProxy().getScheduler()
                    .schedule(plugin, wrapped, delay, period, unit);
        } else if (delay > 0) {
            scheduled = plugin.getProxy().getScheduler()
                    .schedule(plugin, wrapped, delay, unit);
        } else {
            scheduled = plugin.getProxy().getScheduler()
                    .runAsync(plugin, wrapped);
        }

        tasks.put(id, scheduled);
        futures.put(id, future);

        return new BungeeTaskHandle(this, id, scheduled, future, options);
    }

    @Override
    public <T> TaskHandle submit(Callable<T> callable, TaskOptions options) {
        if (plugin == null) return fallback.submit(callable, options);

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

        ScheduledTask scheduled;
        if (repeating) {
            scheduled = ProxyServer.getInstance().getScheduler()
                    .schedule(plugin, runner, delay, period, unit);
        } else if (delay > 0) {
            scheduled = ProxyServer.getInstance().getScheduler()
                    .schedule(plugin, runner, delay, unit);
        } else {
            scheduled = ProxyServer.getInstance().getScheduler()
                    .runAsync(plugin, runner);
        }

        tasks.put(id, scheduled);
        futures.put(id, cf);

        return new BungeeTaskHandle(this, id, scheduled, cf, options);
    }

    @Override
    public boolean isPrimaryThread() {
        return false;
    }

    public Map<String, ScheduledTask> getTasks() {
        return tasks;
    }

    public Map<String, CompletableFuture<?>> getFutures() {
        return futures;
    }

    @Override
    public void cancelAll() {
        if (plugin == null) {
            fallback.cancelAll();
            return;
        }

        for (Map.Entry<String, ScheduledTask> e : tasks.entrySet()) {
            try {
                e.getValue().cancel();
            } catch (Throwable ignored) {
            }
        }
        tasks.clear();

        for (Map.Entry<String, CompletableFuture<?>> e : futures.entrySet()) {
            try {
                e.getValue().cancel(false);
            } catch (Throwable ignored) {
            }
        }
        futures.clear();
    }

}
