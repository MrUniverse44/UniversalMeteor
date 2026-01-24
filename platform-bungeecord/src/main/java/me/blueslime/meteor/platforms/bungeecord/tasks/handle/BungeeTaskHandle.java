package me.blueslime.meteor.platforms.bungeecord.tasks.handle;

import me.blueslime.meteor.platforms.api.tasks.handle.TaskHandle;
import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;
import me.blueslime.meteor.platforms.bungeecord.tasks.BungeePlatformTasks;
import net.md_5.bungee.api.scheduler.ScheduledTask;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class BungeeTaskHandle implements TaskHandle {
    private final String id;
    private final ScheduledTask task;
    private final BungeePlatformTasks main;
    private final CompletableFuture<?> future;
    private final TaskOptions options;

    public BungeeTaskHandle(BungeePlatformTasks main, String id, ScheduledTask task, CompletableFuture<?> future, TaskOptions options) {
        this.id = id;
        this.main = main;
        this.task = task;
        this.future = future;
        this.options = options;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        try {
            task.cancel();
            if (future != null && !future.isDone()) future.cancel(mayInterruptIfRunning);
            main.getTasks().remove(id);
            main.getFutures().remove(id);
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
        return (future != null && future.isDone());
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
