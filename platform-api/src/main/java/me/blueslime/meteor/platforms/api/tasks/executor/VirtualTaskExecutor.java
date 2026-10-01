package me.blueslime.meteor.platforms.api.tasks.executor;

import me.blueslime.meteor.implementation.Implementer;
import me.blueslime.meteor.platforms.api.Project;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class VirtualTaskExecutor
        implements AutoCloseable, Implementer {

    private final ExecutorService executor;

    private final Semaphore concurrency;

    private final AtomicInteger outstanding =
            new AtomicInteger();

    private final AtomicBoolean closed =
            new AtomicBoolean();

    private final int maxOutstanding;

    public VirtualTaskExecutor(
            int maxConcurrent,
            int maxOutstanding
    ) {
        if (maxConcurrent <= 0) {
            throw new IllegalArgumentException(
                    "maxConcurrent must be > 0"
            );
        }

        if (maxOutstanding < maxConcurrent) {
            throw new IllegalArgumentException(
                    "maxOutstanding must be >= maxConcurrent"
            );
        }

        this.maxOutstanding =
                maxOutstanding;

        this.concurrency =
                new Semaphore(
                        maxConcurrent
                );

        String name = fetch(Project.class).name();

        ThreadFactory factory =
                Thread.ofVirtual()
                        .name(
                                name + "-Async-",
                                0
                        )
                        .factory();

        this.executor =
                Executors.newThreadPerTaskExecutor(
                        factory
                );
    }

    public Future<?> submit(
            Runnable task
    ) {
        Objects.requireNonNull(
                task,
                "task"
        );

        ensureOpen();

        int current =
                outstanding.incrementAndGet();

        if (current > maxOutstanding) {
            outstanding.decrementAndGet();

            throw new RejectedExecutionException(
                    "Maximum outstanding async tasks reached: "
                            + maxOutstanding
            );
        }

        FutureTask<Void> future =
                new FutureTask<>(
                        () -> {
                            boolean acquired =
                                    false;

                            try {
                                concurrency.acquire();

                                acquired =
                                        true;

                                task.run();

                                return null;

                            } catch (
                                    InterruptedException exception
                            ) {
                                Thread
                                        .currentThread()
                                        .interrupt();

                                throw exception;

                            } finally {
                                if (acquired) {
                                    concurrency.release();
                                }
                            }
                        }
                ) {

                    @Override
                    protected void done() {
                        outstanding.decrementAndGet();
                    }
                };

        try {
            executor.execute(
                    future
            );

        } catch (
                RejectedExecutionException exception
        ) {
            /*
             * execute() rejected it, so FutureTask.done()
             * will never be invoked automatically.
             */
            outstanding.decrementAndGet();

            throw exception;
        }

        return future;
    }

    public int getOutstandingCount() {
        return outstanding.get();
    }

    public int getAvailablePermits() {
        return concurrency.availablePermits();
    }

    public int getMaxOutstanding() {
        return maxOutstanding;
    }

    public boolean isClosed() {
        return closed.get();
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

        executor.shutdownNow();
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new RejectedExecutionException(
                    "VirtualTaskExecutor is closed"
            );
        }
    }
}