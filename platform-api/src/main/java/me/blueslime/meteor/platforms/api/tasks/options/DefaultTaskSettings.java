package me.blueslime.meteor.platforms.api.tasks.options;

public record DefaultTaskSettings(
        int maxConcurrentAsync,
        int maxOutstandingAsync
) {

    public DefaultTaskSettings {
        if (maxConcurrentAsync <= 0) {
            throw new IllegalArgumentException(
                    "maxConcurrentAsync must be > 0"
            );
        }

        if (
                maxOutstandingAsync <
                        maxConcurrentAsync
        ) {
            throw new IllegalArgumentException(
                    "maxOutstandingAsync must be >= maxConcurrentAsync"
            );
        }
    }

    public static DefaultTaskSettings defaults() {
        return new DefaultTaskSettings(
                256,
                8192
        );
    }

    public static DefaultTaskSettings legacy(
            int asyncPoolSize,
            int syncThreads
    ) {
        if (syncThreads != 1) {
            throw new IllegalArgumentException(
                    "DefaultPlatformTasks requires exactly one sync thread. "
                            + "Use async tasks for parallel work."
            );
        }

        int concurrency =
                Math.max(
                        1,
                        asyncPoolSize
                );

        return new DefaultTaskSettings(
                concurrency,
                Math.max(
                        1024,
                        concurrency * 32
                )
        );
    }
}