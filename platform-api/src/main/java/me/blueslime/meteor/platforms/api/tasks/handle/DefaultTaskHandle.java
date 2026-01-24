package me.blueslime.meteor.platforms.api.tasks.handle;

import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;

import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;

public final class DefaultTaskHandle implements TaskHandle {

    private volatile ScheduledFuture<?> scheduledFuture;
    private final CompletableFuture<?> completion;
    private volatile Future<?> future;
    private final TaskOptions options;
    private final String id;

    public DefaultTaskHandle(String id, TaskOptions options, CompletableFuture<?> completion) {
        this.id = id;
        this.options = options;
        this.completion = completion;
    }

    public void attachFuture(Future<?> f) {
        this.future = f;
    }

    public void attachScheduledFuture(ScheduledFuture<?> sf) {
        this.scheduledFuture = sf;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = false;
        if (scheduledFuture != null) {
            cancelled |= scheduledFuture.cancel(mayInterruptIfRunning);
        }
        if (future != null) {
            cancelled |= future.cancel(mayInterruptIfRunning);
        }
        try {
            if (!completion.isDone()) {
                completion.completeExceptionally(new CancellationException("Cancelled"));
            }
        } catch (Exception ignored) {}
        return cancelled;
    }

    @Override
    public boolean isCancelled() {
        boolean s = scheduledFuture != null && scheduledFuture.isCancelled();
        boolean f = future != null && future.isCancelled();
        return s || f;
    }

    @Override
    public boolean isDone() {
        boolean s = scheduledFuture != null && scheduledFuture.isDone();
        boolean f = future != null && future.isDone();
        return s || f || completion.isDone();
    }

    @Override
    public Optional<CompletableFuture<?>> getFuture() {
        return Optional.of(completion);
    }

    @Override
    public TaskOptions getOptions() {
        return options;
    }
}
