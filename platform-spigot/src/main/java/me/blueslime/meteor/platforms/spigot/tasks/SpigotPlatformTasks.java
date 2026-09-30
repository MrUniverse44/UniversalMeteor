package me.blueslime.meteor.platforms.spigot.tasks;

import me.blueslime.meteor.platforms.api.tasks.DefaultPlatformTasks;
import me.blueslime.meteor.platforms.api.tasks.PlatformTasks;

import me.blueslime.meteor.platforms.api.tasks.executor.VirtualTaskExecutor;

import me.blueslime.meteor.platforms.api.tasks.handle.FutureBackedHandle;
import me.blueslime.meteor.platforms.api.tasks.handle.TaskHandle;

import me.blueslime.meteor.platforms.api.tasks.options.RepeatMode;
import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;

import me.blueslime.meteor.platforms.spigot.tasks.handle.SpigotTaskHandle;

import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Objects;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class SpigotPlatformTasks
        implements PlatformTasks, AutoCloseable {

    private final Map<
            String,
            SpigotTaskHandle
            > tasks =
            new ConcurrentHashMap<>();

    private final AtomicLong idCounter =
            new AtomicLong();

    private final AtomicBoolean closed =
            new AtomicBoolean();

    private final DefaultPlatformTasks fallback;

    private final VirtualTaskExecutor asyncRuntime;

    private final BukkitScheduler scheduler;

    private final JavaPlugin plugin;

    public SpigotPlatformTasks(
            JavaPlugin plugin
    ) {
        this(
                plugin,
                256,
                8192
        );
    }

    public SpigotPlatformTasks(
            JavaPlugin plugin,
            int maxConcurrentAsync,
            int maxOutstandingAsync
    ) {
        this.plugin =
                plugin;

        if (plugin == null) {
            this.scheduler =
                    null;

            this.asyncRuntime =
                    null;

            this.fallback =
                    new DefaultPlatformTasks();

            return;
        }

        this.scheduler =
                plugin
                        .getServer()
                        .getScheduler();

        this.fallback =
                null;

        this.asyncRuntime =
                new VirtualTaskExecutor(
                        maxConcurrentAsync,
                        maxOutstandingAsync
                );
    }

    @Override
    public TaskHandle schedule(
            Runnable task,
            TaskOptions options
    ) {
        Objects.requireNonNull(
                task,
                "task"
        );

        if (scheduler == null) {
            return fallback.schedule(
                    task,
                    options
            );
        }

        ensureOpen();

        TaskOptions resolved =
                normalize(
                        options
                );

        String id =
                Long.toString(
                        idCounter.incrementAndGet()
                );

        SpigotTaskHandle handle =
                new SpigotTaskHandle(
                        id,
                        resolved
                );

        /*
         * Register BEFORE anything can execute.
         */
        tasks.put(
                id,
                handle
        );

        handle
                .getFuture()
                .orElseThrow()
                .whenComplete(
                        (result, throwable) ->
                                tasks.remove(
                                        id,
                                        handle
                                )
                );

        if (resolved.isRepeating()) {
            scheduleRepeating(
                    handle,
                    task,
                    resolved
            );

        } else {
            scheduleOneShot(
                    handle,
                    task,
                    resolved
            );
        }

        return handle;
    }

    @Override
    public <T> TaskHandle submit(
            Callable<T> callable,
            TaskOptions options
    ) {
        Objects.requireNonNull(
                callable,
                "callable"
        );

        if (scheduler == null) {
            return fallback.submit(
                    callable,
                    options
            );
        }

        TaskOptions resolved =
                normalize(
                        options
                );

        if (resolved.isRepeating()) {
            throw new IllegalArgumentException(
                    "Repeating Callable tasks are not supported. "
                            + "Use schedule(Runnable, ...) instead."
            );
        }

        CompletableFuture<T> result =
                new CompletableFuture<>();

        Runnable runner =
                () -> {
                    try {
                        T value =
                                callable.call();

                        result.complete(
                                value
                        );

                    } catch (
                            Throwable throwable
                    ) {
                        result.completeExceptionally(
                                throwable
                        );

                        if (
                                throwable instanceof Error error
                        ) {
                            throw error;
                        }

                        throw new RuntimeException(
                                throwable
                        );
                    }
                };

        TaskHandle delegate =
                schedule(
                        runner,
                        resolved
                );

        return new FutureBackedHandle(
                delegate.getId(),
                delegate,
                result,
                resolved
        );
    }

    private void scheduleOneShot(
            SpigotTaskHandle handle,
            Runnable task,
            TaskOptions options
    ) {
        long delayTicks =
                toTicks(
                        options.getDelay(),
                        options.getDelayUnit()
                );

        if (options.isSync()) {
            BukkitTask scheduled =
                    delayTicks > 0L
                            ? scheduler.runTaskLater(
                            plugin,
                            () ->
                                    executeSync(
                                            handle,
                                            task,
                                            false,
                                            null
                                    ),
                            delayTicks
                    )
                            : scheduler.runTask(
                            plugin,
                            () ->
                                    executeSync(
                                            handle,
                                            task,
                                            false,
                                            null
                                    )
                    );

            handle.attachScheduledTask(
                    scheduled
            );

            return;
        }

        /*
         * Immediate async doesn't need Bukkit at all.
         */
        if (delayTicks <= 0L) {
            executeAsync(
                    handle,
                    task,
                    false,
                    null
            );

            return;
        }

        /*
         * Bukkit is only the timer.
         */
        BukkitTask timer =
                scheduler.runTaskLater(
                        plugin,
                        () ->
                                executeAsync(
                                        handle,
                                        task,
                                        false,
                                        null
                                ),
                        delayTicks
                );

        handle.attachScheduledTask(
                timer
        );
    }

    private void scheduleRepeating(
            SpigotTaskHandle handle,
            Runnable task,
            TaskOptions options
    ) {
        if (
                options.getRepeatMode()
                        == RepeatMode.FIXED_DELAY
        ) {
            scheduleFixedDelay(
                    handle,
                    task,
                    options,
                    toTicks(
                            options.getDelay(),
                            options.getDelayUnit()
                    )
            );

            return;
        }

        scheduleFixedRate(
                handle,
                task,
                options
        );
    }

    private void scheduleFixedRate(
            SpigotTaskHandle handle,
            Runnable task,
            TaskOptions options
    ) {
        long initialDelay =
                toTicks(
                        options.getDelay(),
                        options.getDelayUnit()
                );

        long period =
                Math.max(
                        1L,
                        toTicks(
                                options.getRepeatDelay(),
                                options.getRepeatUnit()
                        )
                );

        /*
         * We intentionally use a synchronous Bukkit timer
         * even for async jobs.
         *
         * The timer itself does virtually no work:
         * it only dispatches to a virtual thread.
         *
         * This keeps Paper tick timing semantics while avoiding
         * long-running work on Bukkit's async scheduler pool.
         */
        BukkitTask timer =
                scheduler.runTaskTimer(
                        plugin,
                        () -> {
                            if (options.isSync()) {
                                executeSync(
                                        handle,
                                        task,
                                        true,
                                        null
                                );

                            } else {
                                executeAsync(
                                        handle,
                                        task,
                                        true,
                                        null
                                );
                            }
                        },
                        initialDelay,
                        period
                );

        handle.attachScheduledTask(
                timer
        );
    }

    private void scheduleFixedDelay(
            SpigotTaskHandle handle,
            Runnable task,
            TaskOptions options,
            long delayTicks
    ) {
        if (
                handle.isDone() ||
                        handle.isCancelled()
        ) {
            return;
        }

        BukkitTask timer =
                scheduler.runTaskLater(
                        plugin,
                        () -> {
                            Runnable next =
                                    () ->
                                            scheduleFixedDelay(
                                                    handle,
                                                    task,
                                                    options,
                                                    Math.max(
                                                            1L,
                                                            toTicks(
                                                                    options.getRepeatDelay(),
                                                                    options.getRepeatUnit()
                                                            )
                                                    )
                                            );

                            if (options.isSync()) {
                                executeSync(
                                        handle,
                                        task,
                                        true,
                                        next
                                );

                            } else {
                                executeAsync(
                                        handle,
                                        task,
                                        true,
                                        next
                                );
                            }
                        },
                        delayTicks
                );

        handle.attachScheduledTask(
                timer
        );
    }

    private void executeSync(
            SpigotTaskHandle handle,
            Runnable task,
            boolean repeating,
            Runnable afterSuccessfulIteration
    ) {
        if (!handle.beginExecution()) {
            return;
        }

        boolean success =
                false;

        try {
            task.run();

            success =
                    true;

            if (!repeating) {
                handle.complete();
            }

        } catch (
                Throwable throwable
        ) {
            handle.fail(
                    throwable
            );

            if (
                    throwable instanceof Error error
            ) {
                throw error;
            }

        } finally {
            if (repeating) {
                handle.finishIteration();

                if (
                        success &&
                                afterSuccessfulIteration != null &&
                                !handle.isDone()
                ) {
                    afterSuccessfulIteration.run();
                }
            }
        }
    }

    private void executeAsync(
            SpigotTaskHandle handle,
            Runnable task,
            boolean repeating,
            Runnable afterSuccessfulIteration
    ) {
        if (!handle.beginExecution()) {
            return;
        }

        try {
            Future<?> future =
                    asyncRuntime.submit(
                            () -> {
                                boolean success =
                                        false;

                                try {
                                    task.run();

                                    success =
                                            true;

                                    if (!repeating) {
                                        handle.complete();
                                    }

                                } catch (
                                        Throwable throwable
                                ) {
                                    handle.fail(
                                            throwable
                                    );

                                    if (
                                            throwable instanceof Error error
                                    ) {
                                        throw error;
                                    }

                                } finally {
                                    if (repeating) {
                                        handle.finishIteration();

                                        if (
                                                success &&
                                                        afterSuccessfulIteration != null &&
                                                        !handle.isDone()
                                        ) {
                                            afterSuccessfulIteration.run();
                                        }
                                    }
                                }
                            }
                    );

            handle.attachExecutionFuture(
                    future
            );

        } catch (
                RejectedExecutionException exception
        ) {
            if (repeating) {
                handle.finishIteration();

                /*
                 * FIXED_DELAY:
                 *
                 * try again during the next interval instead
                 * of killing the logical repeating task.
                 */
                if (
                        afterSuccessfulIteration != null &&
                                !handle.isDone()
                ) {
                    afterSuccessfulIteration.run();
                }

                return;
            }

            handle.fail(
                    exception
            );
        }
    }

    @Override
    public boolean isPrimaryThread() {
        if (plugin == null) {
            return fallback.isPrimaryThread();
        }

        return plugin
                .getServer()
                .isPrimaryThread();
    }

    @Override
    public void cancelAll() {
        if (scheduler == null) {
            fallback.cancelAll();

            return;
        }

        for (
                SpigotTaskHandle handle :
                tasks.values()
        ) {
            try {
                handle.cancel(
                        true
                );

            } catch (
                    RuntimeException ignored
            ) {}
        }

        tasks.clear();
    }

    public int getActiveTaskCount() {
        if (scheduler == null) {
            return fallback
                    .getActiveTaskCount();
        }

        return tasks.size();
    }

    public int getOutstandingAsyncTaskCount() {
        if (asyncRuntime == null) {
            return fallback
                    .getOutstandingAsyncTaskCount();
        }

        return asyncRuntime
                .getOutstandingCount();
    }

    @Override
    public void close() {
        if (
                !closed.compareAndSet(
                        false,
                        true
                )
        ) {
            return;
        }

        cancelAll();

        if (fallback != null) {
            fallback.close();
        }

        if (asyncRuntime != null) {
            asyncRuntime.close();
        }
    }

    private TaskOptions normalize(
            TaskOptions options
    ) {
        return options == null
                ? TaskOptions
                  .asyncBuilder()
                  .build()
                : options;
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new RejectedExecutionException(
                    "PaperPlatformTasks has already been closed"
            );
        }
    }
}