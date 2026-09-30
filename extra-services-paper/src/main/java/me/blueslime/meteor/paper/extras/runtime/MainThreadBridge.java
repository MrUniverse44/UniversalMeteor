package me.blueslime.meteor.paper.extras.runtime;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class MainThreadBridge {

    private final Queue<SyncRequest<?>> queue =
            new ConcurrentLinkedQueue<>();

    private final AtomicInteger pending =
            new AtomicInteger();

    private final AtomicBoolean running =
            new AtomicBoolean(false);

    private final JavaPlugin plugin;

    private final int maxOperationsPerTick;
    private final int maxPending;

    private final long maxBudgetNanos;
    private final long slowOperationNanos;

    private volatile BukkitTask task;

    public MainThreadBridge(
            JavaPlugin plugin,
            int maxOperationsPerTick,
            int maxPending,
            Duration maxBudget,
            Duration slowOperationWarning
    ) {
        this.plugin =
                Objects.requireNonNull(
                        plugin,
                        "plugin"
                );

        if (maxOperationsPerTick <= 0) {
            throw new IllegalArgumentException(
                    "maxOperationsPerTick must be > 0"
            );
        }

        if (maxPending <= 0) {
            throw new IllegalArgumentException(
                    "maxPending must be > 0"
            );
        }

        this.maxOperationsPerTick =
                maxOperationsPerTick;

        this.maxPending =
                maxPending;

        this.maxBudgetNanos =
                Objects.requireNonNull(
                        maxBudget,
                        "maxBudget"
                ).toNanos();

        this.slowOperationNanos =
                Objects.requireNonNull(
                        slowOperationWarning,
                        "slowOperationWarning"
                ).toNanos();

        if (maxBudgetNanos <= 0L) {
            throw new IllegalArgumentException(
                    "maxBudget must be > 0"
            );
        }
    }

    public void start() {
        if (
                !running.compareAndSet(
                        false,
                        true
                )
        ) {
            return;
        }

        try {
            task =
                    Bukkit.getScheduler()
                            .runTaskTimer(
                                    plugin,
                                    this::drain,
                                    1L,
                                    1L
                            );

        } catch (Throwable throwable) {
            running.set(false);

            throw throwable;
        }
    }

    public void shutdown() {
        if (
                !running.compareAndSet(
                        true,
                        false
                )
        ) {
            return;
        }

        BukkitTask currentTask =
                task;

        task = null;

        if (currentTask != null) {
            currentTask.cancel();
        }

        RejectedExecutionException exception =
                new RejectedExecutionException(
                        "MainThreadBridge has been shut down"
                );

        SyncRequest<?> request;

        while (
                (request = queue.poll())
                        != null
        ) {
            pending.decrementAndGet();

            request.future()
                    .completeExceptionally(
                            exception
                    );
        }
    }

    public CompletableFuture<Void> submit(
            Runnable runnable
    ) {
        return submit(
                () -> {
                    runnable.run();
                    return null;
                },
                "sync-operation"
        );
    }

    public CompletableFuture<Void> submit(
            Runnable runnable,
            String debugName
    ) {
        return submit(
                () -> {
                    runnable.run();
                    return null;
                },
                debugName
        );
    }

    public <T> CompletableFuture<T> submit(
            Supplier<T> supplier
    ) {
        return submit(
                supplier,
                "sync-operation"
        );
    }

    public <T> CompletableFuture<T> submit(
            Supplier<T> supplier,
            String debugName
    ) {
        Objects.requireNonNull(
                supplier,
                "supplier"
        );

        /*
         * If we're already on Bukkit's primary
         * thread there is no reason to enqueue it.
         */
        if (Bukkit.isPrimaryThread()) {
            if (!running.get()) {
                return CompletableFuture.failedFuture(
                        new RejectedExecutionException(
                                "MainThreadBridge is not running"
                        )
                );
            }

            try {
                return CompletableFuture.completedFuture(
                        supplier.get()
                );

            } catch (Throwable throwable) {
                return CompletableFuture.failedFuture(
                        throwable
                );
            }
        }

        if (!running.get()) {
            return CompletableFuture.failedFuture(
                    new RejectedExecutionException(
                            "MainThreadBridge is not running"
                    )
            );
        }

        int currentPending =
                pending.incrementAndGet();

        if (
                currentPending >
                        maxPending
        ) {
            pending.decrementAndGet();

            return CompletableFuture.failedFuture(
                    new RejectedExecutionException(
                            "Main-thread bridge queue is full"
                    )
            );
        }

        CompletableFuture<T> future =
                new CompletableFuture<>();

        queue.offer(
                new SyncRequest<>(
                        supplier,
                        future,
                        debugName == null
                                ? "sync-operation"
                                : debugName
                )
        );

        return future;
    }

    public void waitFor(
            Runnable runnable
    ) {
        waitFor(
                () -> {
                    runnable.run();
                    return null;
                }
        );
    }

    public <T> T waitFor(
            Supplier<T> supplier
    ) {
        try {
            return submit(
                    supplier
            ).get();

        } catch (
                InterruptedException exception
        ) {
            Thread.currentThread()
                    .interrupt();

            throw new RuntimeException(
                    "Interrupted while waiting for main thread",
                    exception
            );

        } catch (
                ExecutionException exception
        ) {
            Throwable cause =
                    exception.getCause();

            if (
                    cause instanceof RuntimeException runtime
            ) {
                throw runtime;
            }

            if (
                    cause instanceof Error error
            ) {
                throw error;
            }

            throw new RuntimeException(
                    "Main-thread operation failed",
                    cause
            );
        }
    }

    private void drain() {
        if (!running.get()) {
            return;
        }

        long tickStarted =
                System.nanoTime();

        int processed = 0;

        while (
                processed <
                        maxOperationsPerTick
        ) {
            if (
                    System.nanoTime()
                            - tickStarted
                            >= maxBudgetNanos
            ) {
                break;
            }

            SyncRequest<?> request =
                    queue.poll();

            if (request == null) {
                break;
            }

            pending.decrementAndGet();

            execute(request);

            processed++;
        }
    }

    private <T> void execute(
            SyncRequest<T> request
    ) {
        if (
                request.future()
                        .isDone()
        ) {
            return;
        }

        long started =
                System.nanoTime();

        try {
            T result =
                    request.supplier()
                            .get();

            request.future()
                    .complete(result);

        } catch (Throwable throwable) {
            request.future()
                    .completeExceptionally(
                            throwable
                    );

        } finally {
            long elapsed =
                    System.nanoTime()
                            - started;

            if (
                    elapsed >=
                            slowOperationNanos
            ) {
                plugin
                        .getLogger()
                        .log(
                                Level.WARNING,
                                "Slow MainThreadBridge operation '"
                                        + request.debugName()
                                        + "' took "
                                        + (
                                        elapsed /
                                                1_000_000.0D
                                )
                                        + " ms"
                        );
            }
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public int pendingOperations() {
        return pending.get();
    }

    private record SyncRequest<T>(
            Supplier<T> supplier,
            CompletableFuture<T> future,
            String debugName
    ) {}
}