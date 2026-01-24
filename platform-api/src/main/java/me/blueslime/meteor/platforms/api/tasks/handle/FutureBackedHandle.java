package me.blueslime.meteor.platforms.api.tasks.handle;

import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class FutureBackedHandle implements TaskHandle {
    private final String id;
    private final TaskHandle inner;
    private final CompletableFuture<?> future;
    private final TaskOptions options;

    public FutureBackedHandle(String id, TaskHandle inner, CompletableFuture<?> future, TaskOptions options) {
        this.options = options;
        this.future = future;
        this.inner = inner;
        this.id = id;
    }

    /**
     * Unique identifier for the task (implementation-defined).
     *
     * @return id string or numeric identifier as string
     */
    @Override
    public String getId() {
        return id;
    }

    /**
     * Cancel the task. If true is provided, the task should be interrupted if currently running<br>
     * (if the platform allows); if false, do a gentle cancel.
     *
     * @param mayInterruptIfRunning whether to interrupt running execution
     * @return true if the task was canceled (or already canceled)
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean c = inner.cancel(mayInterruptIfRunning);
        future.cancel(mayInterruptIfRunning);
        return c || future.isCancelled();
    }

    /**
     * Check whether the task was canceled.
     *
     * @return true if canceled
     */
    @Override
    public boolean isCancelled() {
        return future.isCancelled();
    }

    /**
     * Check whether the task has finished execution (normally or exceptionally).
     *
     * @return true if done
     */
    @Override
    public boolean isDone() {
        return future.isDone();
    }

    /**
     * Optionally returns a CompletableFuture that will complete when the task completes.<br>
     * For simple Runnable, the future might be a Void result; for Callables it should carry<br>
     * the actual result.
     *
     * @return optional CompletableFuture of the running task
     */
    @Override
    public Optional<CompletableFuture<?>> getFuture() {
        return Optional.of(future);
    }

    /**
     * Returns the TaskOptions used to schedule this task (snapshot).
     *
     * @return options snapshot
     */
    @Override
    public TaskOptions getOptions() {
        return options;
    }

}
