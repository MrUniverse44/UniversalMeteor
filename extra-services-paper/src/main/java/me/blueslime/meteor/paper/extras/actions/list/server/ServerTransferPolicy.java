package me.blueslime.meteor.paper.extras.actions.list.server;

import java.time.Duration;
import java.util.Objects;

public record ServerTransferPolicy(
    int maxPerWindow,
    Duration resetAt,
    Duration retryAt,
    Duration pendingAt
) {

    public ServerTransferPolicy {
        if (maxPerWindow <= 0) {
            throw new IllegalArgumentException(
                    "maxPerWindow must be > 0"
            );
        }

        Objects.requireNonNull(
                resetAt,
                "resetAt"
        );

        Objects.requireNonNull(
                retryAt,
                "retryAt"
        );

        Objects.requireNonNull(
                pendingAt,
                "pendingAt"
        );

        if (
                resetAt.isZero() ||
                        resetAt.isNegative()
        ) {
            throw new IllegalArgumentException(
                    "resetAt must be > 0"
            );
        }

        if (
                retryAt.isZero() ||
                        retryAt.isNegative()
        ) {
            throw new IllegalArgumentException(
                    "retryAt must be > 0"
            );
        }

        if (
                pendingAt.isZero() ||
                        pendingAt.isNegative()
        ) {
            throw new IllegalArgumentException(
                    "pendingAt must be > 0"
            );
        }
    }

    public static ServerTransferPolicy unlimited() {
        return new ServerTransferPolicy(
                Integer.MAX_VALUE,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        );
    }
}
