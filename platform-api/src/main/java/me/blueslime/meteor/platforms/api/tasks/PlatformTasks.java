package me.blueslime.meteor.platforms.api.tasks;

import me.blueslime.meteor.platforms.api.tasks.handle.TaskHandle;
import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * PlatformTasks<br>
 * <br>
 * Platform-agnostic task scheduling API. Implementations should bridge this API to <br>
 * platform-specific schedulers (Paper/Bukkit scheduler, Velocity scheduler, plain Java Executors, etc.). <br>
 * <br>
 * The interface provides:<br>
 * - simple runSync / runAsync convenience methods,<br>
 * - scheduling with delays and repeats,<br>
 * - submission of Callables returning CompletableFuture,<br>
 * - a flexible TaskOptions payload (metadata, name, priority, repeat config),<br>
 * - a TaskHandle to cancel/inspect tasks,<br>
 * - module-scoped scheduler creation via createModuleTasks(moduleName).<br>
 * <br>
 *
 * Default convenience methods call the core scheduling methods so they benefit from platform implementations.
 */
public interface PlatformTasks {

    /**
     * Run a task synchronously on the primary/main thread immediately.
     *
     * @param task task to run
     * @return handle for the scheduled task (maybe a no-op handle if immediate)
     */
    default TaskHandle runSync(Runnable task) {
        return schedule(task, TaskOptions.syncBuilder().build());
    }

    /**
     * Run a task asynchronously immediately.
     *
     * @param task task to run
     * @return handle for the scheduled task
     */
    default TaskHandle runAsync(Runnable task) {
        return schedule(task, TaskOptions.asyncBuilder().build());
    }

    default long toTicks(long delay, TimeUnit unit) {
        // 1 tick = 50 ms
        long millis = unit.toMillis(delay);
        return Math.max(0, millis / 50);
    }

    /**
     * Run a task synchronously after a delay.
     *
     * @param task  task to run
     * @param delay delay
     * @param unit  time unit
     * @return handle for the scheduled task
     */
    default TaskHandle runSyncLater(Runnable task, long delay, TimeUnit unit) {
        return schedule(task, TaskOptions.syncBuilder().delay(delay, unit).build());
    }

    /**
     * Run a task asynchronously after a delay.
     *
     * @param task  task to run
     * @param delay delay
     * @param unit  time unit
     * @return handle for the scheduled task
     */
    default TaskHandle runAsyncLater(Runnable task, long delay, TimeUnit unit) {
        return schedule(task, TaskOptions.asyncBuilder().delay(delay, unit).build());
    }

    /**
     * Schedule a repeating task at a fixed rate (period between successive executions).
     *
     * @param task        task to run
     * @param initialDelay initial delay before first run
     * @param period      period between runs
     * @param unit        time unit for delay and period
     * @param options     additional options (name, priority, metadata). The repeat fields in options
     *                    will be ignored in favor of the method parameters.
     * @return handle for the scheduled repeating task
     */
    default TaskHandle scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit, TaskOptions options) {
        TaskOptions resolved = TaskOptions.copyOf(options)
                .repeatDelay(period, unit)
                .delay(initialDelay, unit)
                .build();
        return schedule(task, resolved);
    }

    /**
     * Schedule a task using the provided TaskOptions. Implementations must implement this<br>
     * and map the TaskOptions into the platform's scheduler.
     *
     * @param task    runnable to execute
     * @param options options controlling execution (sync/async/delay/repeat/etc.)
     * @return handle representing the scheduled task
     */
    TaskHandle schedule(Runnable task, TaskOptions options);

    /**
     * Submit a Callable task and get a CompletableFuture for its result. Implementations<br>
     * should execute the callable according to the provided TaskOptions (sync/async/delays).
     *
     * @param callable callable to run
     * @param options  scheduling options
     * @param <T>      result type
     * @return TaskHandle containing a CompletableFuture for the callable's result
     */
    <T> TaskHandle submit(Callable<T> callable, TaskOptions options);

    /**
     * Returns true if currently running on the primary/main thread of the platform.
     *
     * @return true when on primary thread
     */
    boolean isPrimaryThread();

    /**
     * Cancel all tasks currently tracked by this PlatformTasks instance.
     * Implementations must attempt to cancel every active task and clear internal registries.
     * This only affects tasks scheduled through this PlatformTasks instance (module-scoped instances
     * should cancel their own tasks).
     */
    void cancelAll();
}
