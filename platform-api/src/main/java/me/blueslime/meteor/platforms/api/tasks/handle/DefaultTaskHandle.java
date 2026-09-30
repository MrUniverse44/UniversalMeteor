package me.blueslime.meteor.platforms.api.tasks.handle;

import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class DefaultTaskHandle<T> implements TaskHandle {

    private final String id;

    private final TaskOptions options;

    private final CompletableFuture<T> completion;

    /**
     * Timer/delay/repeating trigger.
     */
    private final AtomicReference<Future<?>> scheduledFuture =
            new AtomicReference<>();

    /**
     * Current real execution.
     */
    private final AtomicReference<Future<?>> executionFuture =
            new AtomicReference<>();

    /**
     * Prevents two executions of the SAME repeating task
     * from overlapping.
     */
    private final AtomicBoolean running =
            new AtomicBoolean(false);

    /**
     * A repeating task is not "done" after its first iteration.
     */
    private final AtomicBoolean terminal =
            new AtomicBoolean(false);

    private final AtomicBoolean cancelled =
            new AtomicBoolean(false);

    public DefaultTaskHandle(
            String id,
            TaskOptions options,
            CompletableFuture<T> completion
    ) {
        this.id =
                id;

        this.options =
                options;

        this.completion =
                completion;
    }

    public void attachScheduledFuture(
            Future<?> future
    ) {
        if (future == null) {
            return;
        }

        scheduledFuture.set(
                future
        );

        /*
         * Handles:
         *
         * cancel()
         *   ↓
         * attachScheduledFuture()
         *
         * race.
         */
        if (terminal.get()) {
            future.cancel(
                    true
            );
        }
    }

    public void attachExecutionFuture(
            Future<?> future
    ) {
        if (future == null) {
            return;
        }

        executionFuture.set(
                future
        );

        if (cancelled.get()) {
            future.cancel(
                    true
            );
        }
    }

    public boolean beginExecution() {
        if (terminal.get()) {
            return false;
        }

        if (
                !running.compareAndSet(
                        false,
                        true
                )
        ) {
            /*
             * Previous run still active.
             *
             * Skip this occurrence.
             */
            return false;
        }

        /*
         * Cancellation could happen between the two
         * operations above.
         */
        if (terminal.get()) {
            running.set(
                    false
            );

            return false;
        }

        return true;
    }

    public void finishIteration() {
        running.set(
                false
        );
    }

    public boolean isRunning() {
        return running.get();
    }

    public void complete(
            T result
    ) {
        running.set(
                false
        );

        if (
                !terminal.compareAndSet(
                        false,
                        true
                )
        ) {
            return;
        }

        completion.complete(
                result
        );
    }

    public void fail(
            Throwable throwable
    ) {
        running.set(
                false
        );

        if (
                !terminal.compareAndSet(
                        false,
                        true
                )
        ) {
            return;
        }

        Future<?> scheduled =
                scheduledFuture.get();

        if (scheduled != null) {
            scheduled.cancel(
                    false
            );
        }

        completion.completeExceptionally(
                throwable
        );
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean cancel(
            boolean mayInterruptIfRunning
    ) {
        if (
                !terminal.compareAndSet(
                        false,
                        true
                )
        ) {
            return cancelled.get();
        }

        cancelled.set(
                true
        );

        boolean result =
                false;

        Future<?> scheduled =
                scheduledFuture.get();

        if (scheduled != null) {
            result |=
                    scheduled.cancel(
                            false
                    );
        }

        Future<?> execution =
                executionFuture.get();

        if (execution != null) {
            result |=
                    execution.cancel(
                            mayInterruptIfRunning
                    );
        }

        completion.cancel(
                false
        );

        running.set(
                false
        );

        return result ||
                true;
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public boolean isDone() {
        return terminal.get();
    }

    @Override
    public Optional<CompletableFuture<?>> getFuture() {
        return Optional.of(
                completion
        );
    }

    @Override
    public TaskOptions getOptions() {
        return options;
    }
}