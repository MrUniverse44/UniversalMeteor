package me.blueslime.meteor.platforms.paper.tasks.handle;

import me.blueslime.meteor.platforms.api.tasks.handle.TaskHandle;
import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;
import me.blueslime.meteor.utilities.consumer.PluginConsumer;
import org.bukkit.scheduler.BukkitTask;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class PaperTaskHandle implements TaskHandle {

    private final TaskOptions options;
    private final BukkitTask task;
    private final String id;

    public PaperTaskHandle(String id, BukkitTask task, TaskOptions options) {
        this.id = id;
        this.task = task;
        this.options = options;
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
        return PluginConsumer.ofUnchecked(
            () -> {
                task.cancel();
                return true;
            },
            e -> {},
            () -> false
        );
    }

    /**
     * Check whether the task was canceled.
     *
     * @return true if canceled
     */
    @Override
    public boolean isCancelled() {
        return false; /* not tracked */
    }

    /**
     * Check whether the task has finished execution (normally or exceptionally).
     *
     * @return true if done
     */
    @Override
    public boolean isDone() {
        return false; /* not tracked */
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
        return Optional.empty();
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
