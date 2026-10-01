package me.blueslime.meteor.platforms.api.tasks;

import me.blueslime.meteor.implementation.Implementer;
import me.blueslime.meteor.platforms.api.Project;
import me.blueslime.meteor.platforms.api.tasks.executor.VirtualTaskExecutor;
import me.blueslime.meteor.platforms.api.tasks.handle.DefaultTaskHandle;
import me.blueslime.meteor.platforms.api.tasks.handle.TaskHandle;

import me.blueslime.meteor.platforms.api.tasks.options.DefaultTaskSettings;
import me.blueslime.meteor.platforms.api.tasks.options.RepeatMode;
import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultPlatformTasks implements PlatformTasks, AutoCloseable, Implementer {

    /**
     * Active logical tasks.
     */
    private final Map<
            String,
            DefaultTaskHandle<?>
            > tasks =
            new ConcurrentHashMap<>();

    /**
     * Timer only.
     *
     * IMPORTANT:
     * User code NEVER runs here.
     */
    private final ScheduledThreadPoolExecutor scheduler;

    /**
     * Generic platform "sync" executor.
     *
     * Exactly one thread because "sync" must preserve
     * serialized/main-thread-like semantics.
     */
    private final ExecutorService syncExecutor;

    private final AtomicReference<Thread> primaryThread =
            new AtomicReference<>();

    private final AtomicLong idCounter =
            new AtomicLong();

    private final AtomicBoolean closed =
            new AtomicBoolean();

    private final VirtualTaskExecutor asyncRuntime;

    public DefaultPlatformTasks() {
        this(
                DefaultTaskSettings.defaults()
        );
    }

    /**
     * Compatibility with the previous public API.
     */
    @Deprecated
    public DefaultPlatformTasks(
            int asyncPoolSize,
            int syncThreads
    ) {
        this(
                DefaultTaskSettings.legacy(
                        asyncPoolSize,
                        syncThreads
                )
        );
    }

    public DefaultPlatformTasks(
            DefaultTaskSettings settings
    ) {
        Objects.requireNonNull(
                settings,
                "settings"
        );

        String name = fetch(Project.class).name();

        this.asyncRuntime =
                new VirtualTaskExecutor(
                        settings.maxConcurrentAsync(),
                        settings.maxOutstandingAsync()
                );

        ThreadFactory timerFactory =
                Thread.ofPlatform()
                        .daemon(true)
                        .name(
                                name + "-Task-Timer-",
                                0
                        )
                        .factory();

        this.scheduler =
                new ScheduledThreadPoolExecutor(
                        1,
                        timerFactory
                );

        scheduler.setRemoveOnCancelPolicy(
                true
        );

        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(
                false
        );

        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(
                false
        );

        ThreadFactory syncFactory =
                runnable -> {
                    Thread thread =
                            Thread.ofPlatform()
                                    .daemon(true)
                                    .name(
                                            name + "-Task-Main"
                                    )
                                    .unstarted(
                                            runnable
                                    );

                    primaryThread.compareAndSet(
                            null,
                            thread
                    );

                    return thread;
                };

        this.syncExecutor =
                Executors.newSingleThreadExecutor(
                        syncFactory
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

        ensureOpen();

        TaskOptions resolved =
                normalize(
                        options
                );

        String id =
                nextId();

        CompletableFuture<Void> completion =
                new CompletableFuture<>();

        DefaultTaskHandle<Void> handle =
                new DefaultTaskHandle<>(
                        id,
                        resolved,
                        completion
                );

        /*
         * CRITICAL:
         *
         * register BEFORE the task can possibly run.
         *
         * Your old implementation registered AFTER submit(),
         * creating a race with very fast tasks.
         */
        register(
                handle,
                completion
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

        ensureOpen();

        TaskOptions resolved =
                normalize(
                        options
                );

        /*
         * A repeating Callable has no sensible single result.
         *
         * Your old implementation completed the future after the
         * first call while the task kept running.
         */
        if (resolved.isRepeating()) {
            throw new IllegalArgumentException(
                    "Repeating Callable tasks are not supported. "
                            + "Use a repeating Runnable instead."
            );
        }

        String id =
                nextId();

        CompletableFuture<T> completion =
                new CompletableFuture<>();

        DefaultTaskHandle<T> handle =
                new DefaultTaskHandle<>(
                        id,
                        resolved,
                        completion
                );

        register(
                handle,
                completion
        );

        Runnable trigger =
                () ->
                        dispatchCallable(
                                handle,
                                callable,
                                resolved.isSync()
                        );

        if (
                resolved.getDelayNanos() > 0L
        ) {
            try {
                ScheduledFuture<?> future =
                        scheduler.schedule(
                                trigger,
                                resolved.getDelayNanos(),
                                TimeUnit.NANOSECONDS
                        );

                handle.attachScheduledFuture(
                        future
                );

            } catch (
                    RejectedExecutionException exception
            ) {
                handle.fail(
                        exception
                );
            }

        } else {
            trigger.run();
        }

        return handle;
    }

    private void scheduleOneShot(
            DefaultTaskHandle<Void> handle,
            Runnable task,
            TaskOptions options
    ) {
        Runnable trigger =
                () ->
                        dispatchRunnable(
                                handle,
                                task,
                                options.isSync(),
                                false,
                                null
                        );

        if (
                options.getDelayNanos() > 0L
        ) {
            try {
                ScheduledFuture<?> future =
                        scheduler.schedule(
                                trigger,
                                options.getDelayNanos(),
                                TimeUnit.NANOSECONDS
                        );

                handle.attachScheduledFuture(
                        future
                );

            } catch (
                    RejectedExecutionException exception
            ) {
                handle.fail(
                        exception
                );
            }

        } else {
            trigger.run();
        }
    }

    private void scheduleRepeating(
            DefaultTaskHandle<Void> handle,
            Runnable task,
            TaskOptions options
    ) {
        switch (
                options.getRepeatMode()
        ) {
            case FIXED_RATE ->
                    scheduleFixedRate(
                            handle,
                            task,
                            options
                    );

            case FIXED_DELAY ->
                    scheduleFixedDelay(
                            handle,
                            task,
                            options,
                            options.getDelayNanos()
                    );
        }
    }

    /**
     * Fixed rate.
     *
     * The timer may fire while the previous run is active,
     * but DefaultTaskHandle.beginExecution() prevents overlap.
     *
     * That occurrence is skipped instead of queued.
     */
    private void scheduleFixedRate(
            DefaultTaskHandle<Void> handle,
            Runnable task,
            TaskOptions options
    ) {
        try {
            ScheduledFuture<?> future =
                    scheduler.scheduleAtFixedRate(
                            () ->
                                    dispatchRunnable(
                                            handle,
                                            task,
                                            options.isSync(),
                                            true,
                                            null
                                    ),

                            options.getDelayNanos(),

                            options.getRepeatDelayNanos(),

                            TimeUnit.NANOSECONDS
                    );

            handle.attachScheduledFuture(
                    future
            );

        } catch (
                RejectedExecutionException exception
        ) {
            handle.fail(
                    exception
            );
        }
    }

    /**
     * True fixed delay.
     *
     * The next timer is created only AFTER the previous
     * invocation has finished.
     */
    private void scheduleFixedDelay(
            DefaultTaskHandle<Void> handle,
            Runnable task,
            TaskOptions options,
            long delayNanos
    ) {
        if (
                handle.isCancelled() ||
                        handle.isDone()
        ) {
            return;
        }

        try {
            ScheduledFuture<?> future =
                    scheduler.schedule(
                            () ->
                                    dispatchRunnable(
                                            handle,
                                            task,
                                            options.isSync(),
                                            true,
                                            () ->
                                                    scheduleFixedDelay(
                                                            handle,
                                                            task,
                                                            options,
                                                            options.getRepeatDelayNanos()
                                                    )
                                    ),

                            delayNanos,

                            TimeUnit.NANOSECONDS
                    );

            handle.attachScheduledFuture(
                    future
            );

        } catch (
                RejectedExecutionException exception
        ) {
            handle.fail(
                    exception
            );
        }
    }

    private void dispatchRunnable(
            DefaultTaskHandle<Void> handle,
            Runnable task,
            boolean sync,
            boolean repeating,
            Runnable afterSuccessfulIteration
    ) {
        if (
                !handle.beginExecution()
        ) {
            /*
             * For fixed-rate this is intentional:
             * previous iteration is still running.
             */
            return;
        }

        Runnable execution =
                () -> {
                    boolean success =
                            false;

                    try {
                        task.run();

                        success =
                                true;

                        if (!repeating) {
                            handle.complete(
                                    null
                            );
                        }

                    } catch (
                            Throwable throwable
                    ) {
                        handle.fail(
                                throwable
                        );

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
                };

        if (sync) {
            submitSync(
                    handle,
                    execution,
                    repeating,
                    afterSuccessfulIteration
            );

        } else {
            submitAsync(
                    handle,
                    execution,
                    repeating,
                    afterSuccessfulIteration
            );
        }
    }

    private <T> void dispatchCallable(
            DefaultTaskHandle<T> handle,
            Callable<T> callable,
            boolean sync
    ) {
        if (
                !handle.beginExecution()
        ) {
            return;
        }

        Runnable execution =
                () -> {
                    try {
                        T value =
                                callable.call();

                        handle.complete(
                                value
                        );

                    } catch (
                            Throwable throwable
                    ) {
                        handle.fail(
                                throwable
                        );
                    }
                };

        if (sync) {
            submitSync(
                    handle,
                    execution,
                    false,
                    null
            );

        } else {
            submitAsync(
                    handle,
                    execution,
                    false,
                    null
            );
        }
    }

    private void submitSync(
            DefaultTaskHandle<?> handle,
            Runnable execution,
            boolean repeating,
            Runnable retry
    ) {
        try {
            Future<?> future =
                    syncExecutor.submit(
                            execution
                    );

            handle.attachExecutionFuture(
                    future
            );

        } catch (
                RejectedExecutionException exception
        ) {
            if (repeating) {
                handle.finishIteration();
            }

            handle.fail(
                    exception
            );
        }
    }

    private void submitAsync(
            DefaultTaskHandle<?> handle,
            Runnable execution,
            boolean repeating,
            Runnable afterSkippedIteration
    ) {
        try {
            Future<?> future =
                    asyncRuntime.submit(
                            execution
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
                 * Fixed-delay needs to schedule its next attempt.
                 *
                 * Fixed-rate has its own timer, so callback is null.
                 */
                if (
                        afterSkippedIteration != null &&
                                !handle.isDone()
                ) {
                    afterSkippedIteration.run();
                }

                return;
            }

            handle.fail(
                    exception
            );
        }
    }

    private void register(
            DefaultTaskHandle<?> handle,
            CompletableFuture<?> completion
    ) {
        tasks.put(
                handle.getId(),
                handle
        );

        completion.whenComplete(
                (result, throwable) ->
                        tasks.remove(
                                handle.getId(),
                                handle
                        )
        );
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

    @Override
    public boolean isPrimaryThread() {
        Thread expected =
                primaryThread.get();

        return expected != null &&
                Thread.currentThread() ==
                        expected;
    }

    @Override
    public void cancelAll() {
        for (
                DefaultTaskHandle<?> handle :
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
        return tasks.size();
    }

    public int getOutstandingAsyncTaskCount() {
        return asyncRuntime
                .getOutstandingCount();
    }

    public int getAvailableAsyncPermits() {
        return asyncRuntime
                .getAvailablePermits();
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

        scheduler.shutdownNow();

        syncExecutor.shutdownNow();

        asyncRuntime.close();
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new RejectedExecutionException(
                    "PlatformTasks has already been closed"
            );
        }
    }
}