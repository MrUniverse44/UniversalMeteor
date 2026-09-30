package me.blueslime.meteor.platforms.velocity.tasks;

import com.velocitypowered.api.proxy.ProxyServer;

import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;

import me.blueslime.meteor.implementation.Implements;

import me.blueslime.meteor.platforms.api.tasks.DefaultPlatformTasks;
import me.blueslime.meteor.platforms.api.tasks.PlatformTasks;

import me.blueslime.meteor.platforms.api.tasks.executor.VirtualTaskExecutor;

import me.blueslime.meteor.platforms.api.tasks.handle.FutureBackedHandle;
import me.blueslime.meteor.platforms.api.tasks.handle.TaskHandle;

import me.blueslime.meteor.platforms.api.tasks.options.RepeatMode;
import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;

import me.blueslime.meteor.platforms.velocity.tasks.handle.VelocityTaskHandle;

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

public final class VelocityPlatformTasks
        implements PlatformTasks, AutoCloseable {

    private static final int DEFAULT_MAX_CONCURRENT_ASYNC =
            256;

    private static final int DEFAULT_MAX_OUTSTANDING_ASYNC =
            8192;

    private final Map<String, VelocityTaskHandle> tasks =
            new ConcurrentHashMap<>();

    private final AtomicLong idCounter =
            new AtomicLong();

    private final AtomicBoolean closed =
            new AtomicBoolean();

    private final ProxyServer proxy;

    private final Object plugin;

    private final Scheduler scheduler;

    private final DefaultPlatformTasks fallback;

    private final VirtualTaskExecutor asyncRuntime;

    public VelocityPlatformTasks(
            ProxyServer proxy
    ) {
        this(
                proxy,
                DEFAULT_MAX_CONCURRENT_ASYNC,
                DEFAULT_MAX_OUTSTANDING_ASYNC
        );
    }

    public VelocityPlatformTasks(
            ProxyServer proxy,
            int maxConcurrentAsync,
            int maxOutstandingAsync
    ) {
        this.proxy =
                proxy;

        this.plugin =
                proxy == null
                        ? null
                        : Implements.fetch(
                        Object.class,
                        "adapter"
                );

        if (
                proxy == null ||
                        plugin == null
        ) {
            this.scheduler =
                    null;

            this.asyncRuntime =
                    null;

            this.fallback =
                    new DefaultPlatformTasks();

            return;
        }

        this.scheduler =
                proxy.getScheduler();

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

        VelocityTaskHandle handle =
                new VelocityTaskHandle(
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

        delegate
                .getFuture()
                .ifPresent(future ->
                        future.whenComplete(
                                (ignored, throwable) -> {
                                    if (result.isDone()) {
                                        return;
                                    }

                                    if (delegate.isCancelled()) {
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
            VelocityTaskHandle handle,
            Runnable task,
            TaskOptions options
    ) {
        long delayNanos =
                options.getDelayNanos();

        if (delayNanos <= 0L) {
            /*
             * No reason to go through Velocity's own
             * executor for an immediate task.
             */
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
                    scheduler
                            .buildTask(
                                    plugin,
                                    () ->
                                            executeAsync(
                                                    handle,
                                                    task,
                                                    false,
                                                    null
                                            )
                            )
                            .delay(
                                    delayNanos,
                                    TimeUnit.NANOSECONDS
                            )
                            .schedule();

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
            VelocityTaskHandle handle,
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
            VelocityTaskHandle handle,
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
            Scheduler.TaskBuilder builder =
                    scheduler.buildTask(
                            plugin,
                            () ->
                                    executeAsync(
                                            handle,
                                            task,
                                            true,
                                            null
                                    )
                    );

            if (initialDelay > 0L) {
                builder.delay(
                        initialDelay,
                        TimeUnit.NANOSECONDS
                );
            }

            ScheduledTask scheduled =
                    builder
                            .repeat(
                                    period,
                                    TimeUnit.NANOSECONDS
                            )
                            .schedule();

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

    private void scheduleFixedDelay(
            VelocityTaskHandle handle,
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
                    scheduler
                            .buildTask(
                                    plugin,
                                    () ->
                                            executeAsync(
                                                    handle,
                                                    task,
                                                    true,
                                                    next
                                            )
                            )
                            .delay(
                                    delayNanos,
                                    TimeUnit.NANOSECONDS
                            )
                            .schedule();

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
            VelocityTaskHandle handle,
            Runnable task,
            boolean repeating,
            Runnable afterSuccessfulIteration
    ) {
        if (
                !handle.beginExecution()
        ) {
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
            VelocityTaskHandle handle
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
         * Velocity doesn't expose a Bukkit-like
         * primary-thread scheduler model.
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
                VelocityTaskHandle handle :
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
                    "VelocityPlatformTasks has already been closed"
            );
        }
    }
}