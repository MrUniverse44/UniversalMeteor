package me.blueslime.meteor.paper.extras.runtime;

import me.blueslime.meteor.paper.extras.actions.exception.ActionCancelledException;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class CancellationToken {

    private final AtomicBoolean cancelled =
            new AtomicBoolean(false);

    private final AtomicReference<String> reason =
            new AtomicReference<>("Action cancelled");

    public boolean cancel(String reason) {
        if (reason != null && !reason.isBlank()) {
            this.reason.set(reason);
        }

        return cancelled.compareAndSet(
                false,
                true
        );
    }

    public boolean cancel() {
        return cancel("Action cancelled");
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public String reason() {
        return reason.get();
    }

    public void throwIfCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            cancel("Action thread interrupted");
        }

        if (cancelled.get()) {
            throw new ActionCancelledException(
                    reason.get()
            );
        }
    }
}
