package me.blueslime.meteor.platforms.spigot.tasks;

import me.blueslime.meteor.platforms.api.tasks.DefaultPlatformTasks;
import me.blueslime.meteor.platforms.api.tasks.PlatformTasks;
import me.blueslime.meteor.platforms.api.tasks.handle.FutureBackedHandle;
import me.blueslime.meteor.platforms.api.tasks.handle.TaskHandle;
import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;
import me.blueslime.meteor.platforms.spigot.tasks.handle.SpigotTaskHandle;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SpigotPlatformTasks implements PlatformTasks {

    private final Map<String, TaskHandle> fallbackHandles = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> bukkitTasks = new ConcurrentHashMap<>();
    private final DefaultPlatformTasks fallback;
    private final BukkitScheduler scheduler;
    private final JavaPlugin plugin;

    public SpigotPlatformTasks(JavaPlugin plugin) {
        this.plugin = plugin;
        if (plugin != null) {
            this.scheduler = plugin.getServer().getScheduler();
            this.fallback = null;
        } else {
            this.scheduler = null;
            this.fallback = new DefaultPlatformTasks();
        }
    }

    /**
     * Schedule a task using the provided TaskOptions. Implementations must implement this<br>
     * and map the TaskOptions into the platform's scheduler.
     *
     * @param task    runnable to execute
     * @param options options controlling execution (sync/async/delay/repeat/etc.)
     * @return handle representing the scheduled task
     */
    @Override
    public TaskHandle schedule(Runnable task, TaskOptions options) {
        if (scheduler == null) return fallback.schedule(task, options);

        boolean sync = options != null && options.isSync();
        long delay = options != null ? options.getDelay() : 0;
        long repeat = options != null ? options.getRepeatDelay() : 0;
        TimeUnit unit = options != null ? options.getTimeUnit() : TimeUnit.MILLISECONDS;
        long ticksDelay = toTicks(delay, unit);
        long ticksRepeat = repeat > 0 ? toTicks(repeat, unit) : 0;

        BukkitTask bt;
        if (repeat > 0) {
            if (sync) {
                bt = scheduler.runTaskTimer(plugin, task, ticksDelay, ticksRepeat);
            } else {
                bt = scheduler.runTaskTimerAsynchronously(plugin, task, ticksDelay, ticksRepeat);
            }
        } else {
            if (sync) {
                if (ticksDelay > 0) bt = scheduler.runTaskLater(plugin, task, ticksDelay);
                else bt = scheduler.runTask(plugin, task);
            } else {
                if (ticksDelay > 0) bt = scheduler.runTaskLaterAsynchronously(plugin, task, ticksDelay);
                else bt = scheduler.runTaskAsynchronously(plugin, task);
            }
        }

        String id = String.valueOf(bt.getTaskId());
        SpigotTaskHandle handle = new SpigotTaskHandle(id, bt, options);
        bukkitTasks.put(id, bt);
        return handle;
    }

    /**
     * Submit a Callable task and get a CompletableFuture for its result. Implementations<br>
     * should execute the callable according to the provided TaskOptions (sync/async/delays).
     *
     * @param callable callable to run
     * @param options  scheduling options
     * @return TaskHandle containing a CompletableFuture for the callable's result
     */
    @Override
    public <T> TaskHandle submit(Callable<T> callable, TaskOptions options) {
        if (scheduler == null) return fallback.submit(callable, options);

        CompletableFuture<T> cf = new CompletableFuture<>();
        Runnable runner = () -> {
            try {
                T res = callable.call();
                cf.complete(res);
            } catch (Throwable t) {
                cf.completeExceptionally(t);
            }
        };

        TaskHandle h = schedule(runner, options == null ? TaskOptions.asyncBuilder().build() : options);
        return new FutureBackedHandle(h.getId(), h, cf, options);
    }

    /**
     * Returns true if currently running on the primary/main thread of the platform.
     *
     * @return true when on primary thread
     */
    @Override
    public boolean isPrimaryThread() {
        if (plugin == null) return fallback.isPrimaryThread();
        return plugin.getServer().isPrimaryThread();
    }

    @Override
    public void cancelAll() {
        if (scheduler == null) {
            fallback.cancelAll();
            return;
        }

        for (Map.Entry<String, BukkitTask> e : bukkitTasks.entrySet()) {
            try {
                e.getValue().cancel();
            } catch (Exception ignored) {}
        }
        bukkitTasks.clear();

        for (Map.Entry<String, TaskHandle> e : fallbackHandles.entrySet()) {
            try {
                e.getValue().cancel(false);
            } catch (Exception ignored) {}
        }
        fallbackHandles.clear();
    }
}
