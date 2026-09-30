package me.blueslime.meteor.platforms.bungeecord.tasks;

import me.blueslime.meteor.platforms.api.tasks.DefaultPlatformTasks;
import me.blueslime.meteor.platforms.api.tasks.PlatformTasks;

import me.blueslime.meteor.platforms.api.tasks.executor.VirtualTaskExecutor;

import me.blueslime.meteor.platforms.api.tasks.handle.FutureBackedHandle;
import me.blueslime.meteor.platforms.api.tasks.handle.TaskHandle;

import me.blueslime.meteor.platforms.api.tasks.options.RepeatMode;
import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;

import me.blueslime.meteor.platforms.bungeecord.tasks.handle.BungeeTaskHandle;

import net.md_5.bungee.api.plugin.Plugin;

import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.api.scheduler.TaskScheduler;

import java.util.Map;
import java.util.Objects;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class BungeePlatformTasks
        implements PlatformTasks, AutoCloseable {

    private static final int DEFAULT_MAX_CONCURRENT_ASYNC =
            256;

    private static final int DEFAULT_MAX_OUTSTANDING_ASYNC =
            8192;

    private final Map<String, BungeeTaskHandle> tasks =
            new ConcurrentHashMap<>();

    private final AtomicLong idCounter =
            new AtomicLong();

    private final AtomicBoolean closed =
            new AtomicBoolean();

    private final Plugin plugin;

    private final TaskScheduler scheduler;

    private final DefaultPlatformTasks fallback;

    private final VirtualTaskExecutor asyncRuntime;

    public BungeePlatformTasks(
            Plugin plugin
    ) {
        this(
                plugin,
                DEFAULT_MAX_CONCURRENT_ASYNC,
                DEFAULT_MAX_OUTSTANDING_ASYNC
        );
    }

    public BungeePlatformTasks(
            Plugin plugin,
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
                        .getProxy()
                        .getScheduler();

        this.asyncRuntime =
                new VirtualTaskExecutor(
                        maxConcurrentAsync,
                        maxOutstandingAsync
                );

        this.fallback =
                null;
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
                nextId();

        BungeeTaskHandle handle =
                new BungeeTaskHandle(
                        id,
                        resolved
                );

        register(
                handle
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

        ensureOpen();

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
                            Exception exception
                    ) {
                        result.completeExceptionally(
                                exception
                        );

                        throw new CompletionException(
                                exception
                        );

                    } catch (
                            Error error
                    ) {
                        result.completeExceptionally(
                                error
                        );

                        throw error;
                    }
                };

        TaskHandle delegate =
                schedule(
                        runner,
                        resolved
                );

        /*
         * If scheduling/execution fails before callable itself
         * gets the opportunity to complete result, propagate
         * the delegate failure too.
         */
        delegate
                .getFuture()
                .ifPresent(future ->
                        future.whenComplete(
                                (ignored, throwable) -> {
                                    if (
                                            result.isDone()
                                    ) {
                                        return;
                                    }

                                    if (
                                            delegate.isCancelled()
                                    ) {
                                        result.cancel(
                                                false
                                        );

                                        return;
                                    }

                                    if (throwable != null) {
                                        result.completeExceptionally(
                                                throwable
                                        );
                                    }
                                }
                        )
                );

        return new FutureBackedHandle(
                delegate.getId(),
                delegate,
                result,
                resolved
        );
    }

    private void scheduleOneShot(
            BungeeTaskHandle handle,
            Runnable task,
            TaskOptions options
    ) {
        long delayNanos =
                options.getDelayNanos();

        /*
         * Immediate task:
         *
         * Bungee scheduler isn't necessary.
         */
        if (delayNanos <= 0L) {
            executeAsync(
                    handle,
                    task,
                    false,
                    null
            );

            return;
        }

        try {
            ScheduledTask scheduled =
                    scheduler.schedule(
                            plugin,
                            () ->
                                    executeAsync(
                                            handle,
                                            task,
                                            false,
                                            null
                                    ),
                            delayNanos,
                            TimeUnit.NANOSECONDS
                    );

            handle.attachScheduledTask(
                    scheduled
            );

        } catch (
                RuntimeException exception
        ) {
            handle.fail(
                    exception
            );
        }
    }

    private void scheduleRepeating(
            BungeeTaskHandle handle,
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
                    options.getDelayNanos()
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
            BungeeTaskHandle handle,
            Runnable task,
            TaskOptions options
    ) {
        long initialDelay =
                options.getDelayNanos();

        long period =
                Math.max(
                        1L,
                        options.getRepeatDelayNanos()
                );

        try {
            ScheduledTask scheduled =
                    scheduler.schedule(
                            plugin,
                            () ->
                                    executeAsync(
                                            handle,
                                            task,
                                            true,
                                            null
                                    ),
                            initialDelay,
                            period,
                            TimeUnit.NANOSECONDS
                    );

            handle.attachScheduledTask(
                    scheduled
            );

        } catch (
                RuntimeException exception
        ) {
            handle.fail(
                    exception
            );
        }
    }

    /**
     * Fixed delay is implemented manually:
     *
     * timer
     *   -> execute
     *   -> finish
     *   -> wait repeatDelay
     *   -> execute
     */
    private void scheduleFixedDelay(
            BungeeTaskHandle handle,
            Runnable task,
            TaskOptions options,
            long delayNanos
    ) {
        if (
                handle.isDone() ||
                        handle.isCancelled()
        ) {
            return;
        }

        Runnable next =
                () ->
                        scheduleFixedDelay(
                                handle,
                                task,
                                options,
                                Math.max(
                                        1L,
                                        options.getRepeatDelayNanos()
                                )
                        );

        /*
         * Avoid going through Bungee for a zero initial delay.
         */
        if (delayNanos <= 0L) {
            executeAsync(
                    handle,
                    task,
                    true,
                    next
            );

            return;
        }

        try {
            ScheduledTask scheduled =
                    scheduler.schedule(
                            plugin,
                            () ->
                                    executeAsync(
                                            handle,
                                            task,
                                            true,
                                            next
                                    ),
                            delayNanos,
                            TimeUnit.NANOSECONDS
                    );

            handle.attachScheduledTask(
                    scheduled
            );

        } catch (
                RuntimeException exception
        ) {
            handle.fail(
                    exception
            );
        }
    }

    private void executeAsync(
            BungeeTaskHandle handle,
            Runnable task,
            boolean repeating,
            Runnable afterSuccessfulIteration
    ) {
        if (
                !handle.beginExecution()
        ) {
            /*
             * Fixed-rate overlap:
             *
             * previous iteration is still active,
             * so this occurrence is intentionally skipped.
             */
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
                                        RuntimeException exception
                                ) {
                                    handle.fail(
                                            exception
                                    );

                                } catch (
                                        Error error
                                ) {
                                    handle.fail(
                                            error
                                    );

                                    throw error;

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
                 * Fixed-rate:
                 *     callback is null, skip this occurrence.
                 *
                 * Fixed-delay:
                 *     schedule another attempt.
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

    private void register(
            BungeeTaskHandle handle
    ) {
        tasks.put(
                handle.getId(),
                handle
        );

        handle
                .getFuture()
                .orElseThrow()
                .whenComplete(
                        (result, throwable) ->
                                tasks.remove(
                                        handle.getId(),
                                        handle
                                )
                );
    }

    @Override
    public boolean isPrimaryThread() {
        /*
         * Bungee's scheduler is asynchronous and does not expose
         * a Bukkit-style primary task thread.
         */
        return false;
    }

    @Override
    public void cancelAll() {
        if (scheduler == null) {
            fallback.cancelAll();

            return;
        }

        for (
                BungeeTaskHandle handle :
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
        if (fallback != null) {
            return fallback
                    .getActiveTaskCount();
        }

        return tasks.size();
    }

    public int getOutstandingAsyncTaskCount() {
        if (fallback != null) {
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

        if (asyncRuntime != null) {
            asyncRuntime.close();
        }

        if (fallback != null) {
            fallback.close();
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

    private String nextId() {
        return Long.toString(
                idCounter.incrementAndGet()
        );
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new RejectedExecutionException(
                    "BungeePlatformTasks has already been closed"
            );
        }
    }
}