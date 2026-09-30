package me.blueslime.meteor.paper.extras.actions.list.server;

import java.time.Duration;
import java.util.Objects;

public final class ServerTransferSettings {

    private Duration processInterval =
            Duration.ofMillis(50);

    private int maxQueueSizePerServer =
            10_000;

    private int maxGlobalQueueSize =
            50_000;

    private int maxMessagesPerSyncBatch =
            32;

    private ServerTransferSettings() {}

    public static ServerTransferSettings builder() {
        return new ServerTransferSettings();
    }

    public ServerTransferSettings processInterval(
            Duration duration
    ) {
        this.processInterval =
                Objects.requireNonNull(
                        duration
                );

        return this;
    }

    public ServerTransferSettings maxQueueSizePerServer(
            int value
    ) {
        this.maxQueueSizePerServer =
                value;

        return this;
    }

    public ServerTransferSettings maxGlobalQueueSize(
            int value
    ) {
        this.maxGlobalQueueSize =
                value;

        return this;
    }

    public ServerTransferSettings maxMessagesPerSyncBatch(
            int value
    ) {
        this.maxMessagesPerSyncBatch =
                value;

        return this;
    }

    public Duration processInterval() {
        return processInterval;
    }

    public int maxQueueSizePerServer() {
        return maxQueueSizePerServer;
    }

    public int maxGlobalQueueSize() {
        return maxGlobalQueueSize;
    }

    public int maxMessagesPerSyncBatch() {
        return maxMessagesPerSyncBatch;
    }

    public ServerTransferSettings validate() {
        if (
                processInterval.isZero() ||
                        processInterval.isNegative()
        ) {
            throw new IllegalArgumentException(
                    "processInterval must be > 0"
            );
        }

        if (
                maxQueueSizePerServer <= 0 ||
                        maxGlobalQueueSize <= 0 ||
                        maxMessagesPerSyncBatch <= 0
        ) {
            throw new IllegalArgumentException(
                    "ServerTransferSettings limits must be > 0"
            );
        }

        return this;
    }
}
