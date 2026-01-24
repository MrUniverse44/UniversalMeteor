package me.blueslime.meteor.platforms.velocity.tasks.handle;

import com.velocitypowered.api.scheduler.ScheduledTask;
import me.blueslime.meteor.platforms.api.tasks.handle.TaskHandle;
import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class VelocityTaskHandle implements TaskHandle {
    private final CompletableFuture<?> future;
    private final TaskOptions options;
    private final ScheduledTask task;
    private final String id;

    public VelocityTaskHandle(String id, ScheduledTask task, CompletableFuture<?> future, TaskOptions options) {
        this.id = id;
        this.task = task;
        this.future = future;
        this.options = options;
    }

    @Override public String getId() { return id; }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        try {
            task.cancel();
            if (future != null && !future.isDone()) future.cancel(mayInterruptIfRunning);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean isCancelled() {
        return future != null && future.isCancelled();
    }
    @Override
    public boolean isDone() {
        return future != null && future.isDone();
    }
    @Override
    public Optional<CompletableFuture<?>> getFuture() {
        return Optional.ofNullable(future);
    }
    @Override
    public TaskOptions getOptions() {
        return options;
    }
}