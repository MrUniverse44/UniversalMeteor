package me.blueslime.meteor.platforms.api.tasks.handle;

import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;

import java.util.Objects;
import java.util.Optional;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared lifecycle implementation for platform-backed tasks.
 *
 * @param <S> platform-specific scheduled task type
 */
public abstract class AbstractPlatformTaskHandle<S> implements TaskHandle {

    private final String id;

    private final TaskOptions options;

    /**
     * Logical task completion.
     *
     * One-shot:
     *     completes when execution finishes.
     *
     * Repeating:
     *     remains incomplete until cancelled or failed.
     */
    private final CompletableFuture<Void> completion =
            new CompletableFuture<>();

    /**
     * Platform timer/task.
     *
     * Examples:
     *
     * Paper      -> BukkitTask
     * BungeeCord -> ScheduledTask
     * Velocity   -> ScheduledTask
     */
    private final AtomicReference<S> scheduledTask =
            new AtomicReference<>();

    /**
     * Real asynchronous execution.
     *
     * Usually backed by VirtualTaskExecutor.
     */
    private final AtomicReference<Future<?>> executionFuture =
            new AtomicReference<>();

    /**
     * Prevents two executions of the same logical repeating
     * task from overlapping.
     */
    private final AtomicBoolean running =
            new AtomicBoolean();

    private final AtomicBoolean cancelled =
            new AtomicBoolean();

    /**
     * Terminal means:
     *
     * - completed
     * - failed
     * - cancelled
     */
    private final AtomicBoolean terminal =
            new AtomicBoolean();

    /**
     * Remembers whether cancellation requested interruption.
     *
     * This is useful for the race:
     *
     * cancel(true)
     *      ↓
     * async Future gets attached afterwards
     */
    private final AtomicBoolean interruptOnCancel =
            new AtomicBoolean();

    protected AbstractPlatformTaskHandle(
            String id,
            TaskOptions options
    ) {
        this.id =
                Objects.requireNonNull(
                        id,
                        "id"
                );

        this.options =
                Objects.requireNonNull(
                        options,
                        "options"
                );
    }

    /**
     * Attaches/replaces the native platform timer.
     *
     * Fixed-delay repeating tasks replace this reference
     * every time a new timer is created.
     */
    public final void attachScheduledTask(
            S task
    ) {
        if (task == null) {
            return;
        }

        /*
         * Task was cancelled/failed before the platform
         * returned its scheduled object.
         */
        if (terminal.get()) {
            cancelScheduledSafely(
                    task
            );

            return;
        }

        scheduledTask.set(
                task
        );

        /*
         * Protect against:
         *
         * terminal check
         *     ↓
         * cancel()
         *     ↓
         * scheduledTask.set()
         */
        if (
                terminal.get() &&
                        scheduledTask.compareAndSet(
                                task,
                                null
                        )
        ) {
            cancelScheduledSafely(
                    task
            );
        }
    }

    /**
     * Attaches the currently-running async execution.
     */
    public final void attachExecutionFuture(
            Future<?> future
    ) {
        if (future == null) {
            return;
        }

        if (terminal.get()) {
            future.cancel(
                    interruptOnCancel.get()
            );

            return;
        }

        executionFuture.set(
                future
        );

        /*
         * Protect against cancellation between the
         * initial terminal check and set().
         */
        if (
                terminal.get() &&
                        executionFuture.compareAndSet(
                                future,
                                null
                        )
        ) {
            future.cancel(
                    interruptOnCancel.get()
            );
        }
    }

    /**
     * Attempts to begin one execution of this logical task.
     *
     * For repeating tasks this provides no-overlap semantics.
     *
     * @return true when execution may start
     */
    public final boolean beginExecution() {
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
             * Previous execution still active.
             */
            return false;
        }

        /*
         * Cancellation/failure might have happened after
         * the first terminal check.
         */
        if (terminal.get()) {
            running.set(
                    false
            );

            return false;
        }

        return true;
    }

    /**
     * Marks one repeating iteration as finished without
     * completing the logical repeating task.
     */
    public final void finishIteration() {
        running.set(
                false
        );
    }

    public final boolean isRunning() {
        return running.get();
    }

    /**
     * Completes a one-shot task successfully.
     */
    public final void complete() {
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

        /*
         * We don't need to cancel the platform task:
         * for a successful one-shot it already fired.
         *
         * Drop references so user-held handles don't retain
         * platform objects unnecessarily.
         */
        scheduledTask.set(
                null
        );

        executionFuture.set(
                null
        );

        completion.complete(
                null
        );
    }

    /**
     * Terminates this logical task exceptionally.
     *
     * For repeating tasks this also cancels future iterations.
     */
    public final void fail(
            Throwable throwable
    ) {
        Objects.requireNonNull(
                throwable,
                "throwable"
        );

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

        cancelCurrentScheduledTask();

        /*
         * Do not cancel executionFuture here.
         *
         * fail() is commonly invoked FROM that execution.
         */
        executionFuture.set(
                null
        );

        completion.completeExceptionally(
                throwable
        );
    }

    @Override
    public final boolean cancel(
            boolean mayInterruptIfRunning
    ) {
        /*
         * Allow:
         *
         * cancel(false)
         * cancel(true)
         *
         * to escalate interruption if necessary.
         */
        if (mayInterruptIfRunning) {
            interruptOnCancel.set(
                    true
            );
        }

        if (cancelled.get()) {
            if (mayInterruptIfRunning) {
                Future<?> execution =
                        executionFuture.get();

                if (execution != null) {
                    execution.cancel(
                            true
                    );
                }
            }

            return true;
        }

        if (
                !terminal.compareAndSet(
                        false,
                        true
                )
        ) {
            /*
             * Already completed or failed normally.
             */
            return false;
        }

        cancelled.set(
                true
        );

        running.set(
                false
        );

        cancelCurrentScheduledTask();

        Future<?> execution =
                executionFuture.getAndSet(
                        null
                );

        if (execution != null) {
            execution.cancel(
                    mayInterruptIfRunning
            );
        }

        completion.cancel(
                false
        );

        return true;
    }

    @Override
    public final String getId() {
        return id;
    }

    @Override
    public final boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public final boolean isDone() {
        return terminal.get();
    }

    @Override
    public final Optional<CompletableFuture<?>> getFuture() {
        return Optional.of(
                completion
        );
    }

    @Override
    public final TaskOptions getOptions() {
        return options;
    }

    /**
     * Platform-specific scheduled task cancellation.
     */
    protected abstract void cancelScheduled(
            S scheduled
    );

    private void cancelCurrentScheduledTask() {
        S scheduled =
                scheduledTask.getAndSet(
                        null
                );

        if (scheduled == null) {
            return;
        }

        cancelScheduledSafely(
                scheduled
        );
    }

    private void cancelScheduledSafely(
            S scheduled
    ) {
        try {
            cancelScheduled(
                    scheduled
            );

        } catch (
                RuntimeException ignored
        ) {
            /*
             * Cancellation should never prevent the logical
             * task from reaching terminal state.
             */
        }
    }
}