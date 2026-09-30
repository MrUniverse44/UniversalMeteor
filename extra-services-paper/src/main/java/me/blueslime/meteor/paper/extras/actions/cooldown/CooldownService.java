package me.blueslime.meteor.paper.extras.actions.cooldown;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public final class CooldownService {

    private final ConcurrentMap<CooldownKey, Long> cooldowns =
            new ConcurrentHashMap<>();

    private final AtomicLong operations =
            new AtomicLong();

    public CooldownCheck acquire(
            String scope,
            String key,
            Duration duration
    ) {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(key);
        Objects.requireNonNull(duration);

        if (
                duration.isZero() ||
                        duration.isNegative()
        ) {
            return new CooldownCheck(
                    true,
                    Duration.ZERO
            );
        }

        long now =
                System.nanoTime();

        long durationNanos =
                duration.toNanos();

        CooldownKey cooldownKey =
                new CooldownKey(
                        scope,
                        key
                );

        while (true) {
            Long current =
                    cooldowns.get(cooldownKey);

            if (current != null) {
                long remaining =
                        current - now;

                if (remaining > 0L) {
                    cleanupSometimes(now);

                    return new CooldownCheck(
                            false,
                            Duration.ofNanos(
                                    remaining
                            )
                    );
                }
            }

            long next =
                    now + durationNanos;

            boolean acquired;

            if (current == null) {
                acquired =
                        cooldowns.putIfAbsent(
                                cooldownKey,
                                next
                        ) == null;
            } else {
                acquired =
                        cooldowns.replace(
                                cooldownKey,
                                current,
                                next
                        );
            }

            if (acquired) {
                cleanupSometimes(now);

                return new CooldownCheck(
                        true,
                        Duration.ZERO
                );
            }
        }
    }

    public void remove(
            String scope,
            String key
    ) {
        cooldowns.remove(
                new CooldownKey(
                        scope,
                        key
                )
        );
    }

    public void clear() {
        cooldowns.clear();
    }

    private void cleanupSometimes(
            long now
    ) {
        long operation =
                operations.incrementAndGet();

        if ((operation & 1023L) != 0L) {
            return;
        }

        cooldowns.entrySet()
                .removeIf(
                        entry ->
                                entry.getValue() <= now
                );
    }

    private record CooldownKey(
            String scope,
            String key
    ) {}

    public record CooldownCheck(
            boolean acquired,
            Duration remaining
    ) {}
}
