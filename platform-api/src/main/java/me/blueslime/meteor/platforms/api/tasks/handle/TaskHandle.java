package me.blueslime.meteor.platforms.api.tasks.handle;

import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a scheduled or submitted task. Implementations must return a concrete handle<br>
 * that allows cancellation and inspection.
 */
public interface TaskHandle {

    /**
     * Unique identifier for the task (implementation-defined).
     *
     * @return id string or numeric identifier as string
     */
    String getId();

    /**
     * Cancel the task. If true is provided, the task should be interrupted if currently running<br>
     * (if the platform allows); if false, do a gentle cancel.
     *
     * @param mayInterruptIfRunning whether to interrupt running execution
     * @return true if the task was canceled (or already canceled)
     */
    boolean cancel(boolean mayInterruptIfRunning);

    /**
     * Cancel the task without interrupting running tasks.
     *
     * @return true if the task was canceled
     */
    default boolean cancel() {
        return cancel(false);
    }

    /**
     * Check whether the task was canceled.
     *
     * @return true if canceled
     */
    boolean isCancelled();

    /**
     * Check whether the task has finished execution (normally or exceptionally).
     *
     * @return true if done
     */
    boolean isDone();

    /**
     * Optionally returns a CompletableFuture that will complete when the task completes.<br>
     * For simple Runnable, the future might be a Void result; for Callables it should carry<br>
     * the actual result.
     *
     * @return optional CompletableFuture of the running task
     */
    Optional<CompletableFuture<?>> getFuture();

    /**
     * Returns the TaskOptions used to schedule this task (snapshot).
     *
     * @return options snapshot
     */
    TaskOptions getOptions();
}
