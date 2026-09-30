package me.blueslime.meteor.paper.extras.runtime;

import java.time.Duration;
import java.util.Objects;

public final class ExecutionRuntimeSettings {

    private int maxSyncOperationsPerTick =
            128;

    private int maxPendingSyncOperations =
            8192;

    private Duration maxSyncBudget =
            Duration.ofMillis(2);

    private Duration slowSyncWarning =
            Duration.ofMillis(10);

    private ExecutionRuntimeSettings() {}

    public static ExecutionRuntimeSettings builder() {
        return new ExecutionRuntimeSettings();
    }

    public ExecutionRuntimeSettings maxSyncOperationsPerTick(
            int value
    ) {
        this.maxSyncOperationsPerTick =
                value;

        return this;
    }

    public ExecutionRuntimeSettings maxPendingSyncOperations(
            int value
    ) {
        this.maxPendingSyncOperations =
                value;

        return this;
    }

    public ExecutionRuntimeSettings maxSyncBudget(
            Duration duration
    ) {
        this.maxSyncBudget =
                Objects.requireNonNull(
                        duration
                );

        return this;
    }

    public ExecutionRuntimeSettings slowSyncWarning(
            Duration duration
    ) {
        this.slowSyncWarning =
                Objects.requireNonNull(
                        duration
                );

        return this;
    }

    public int maxSyncOperationsPerTick() {
        return maxSyncOperationsPerTick;
    }

    public int maxPendingSyncOperations() {
        return maxPendingSyncOperations;
    }

    public Duration maxSyncBudget() {
        return maxSyncBudget;
    }

    public Duration slowSyncWarning() {
        return slowSyncWarning;
    }

    public ExecutionRuntimeSettings validate() {
        if (
                maxSyncOperationsPerTick <= 0 ||
                        maxPendingSyncOperations <= 0
        ) {
            throw new IllegalArgumentException(
                    "Execution runtime limits must be > 0"
            );
        }

        if (
                maxSyncBudget.isZero() ||
                        maxSyncBudget.isNegative()
        ) {
            throw new IllegalArgumentException(
                    "maxSyncBudget must be > 0"
            );
        }

        return this;
    }
}