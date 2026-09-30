package me.blueslime.meteor.platforms.api.tasks.options;

import me.blueslime.meteor.platforms.api.tasks.priority.TaskPriority;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class TaskOptions {

    private final boolean sync;

    private final long delay;
    private final TimeUnit delayUnit;

    private final long repeatDelay;
    private final TimeUnit repeatUnit;

    private final RepeatMode repeatMode;

    private final TaskPriority priority;

    private final String name;

    private final Map<String, Object> metadata;

    private TaskOptions(
            boolean sync,

            long delay,
            TimeUnit delayUnit,

            long repeatDelay,
            TimeUnit repeatUnit,

            RepeatMode repeatMode,

            TaskPriority priority,

            String name,

            Map<String, Object> metadata
    ) {
        this.sync =
                sync;

        this.delay =
                Math.max(
                        0L,
                        delay
                );

        this.delayUnit =
                delayUnit == null
                        ? TimeUnit.MILLISECONDS
                        : delayUnit;

        this.repeatDelay =
                Math.max(
                        0L,
                        repeatDelay
                );

        this.repeatUnit =
                repeatUnit == null
                        ? TimeUnit.MILLISECONDS
                        : repeatUnit;

        this.repeatMode =
                repeatMode == null
                        ? RepeatMode.FIXED_RATE
                        : repeatMode;

        this.priority =
                priority == null
                        ? TaskPriority.NORMAL
                        : priority;

        this.name =
                name;

        this.metadata =
                metadata == null ||
                        metadata.isEmpty()
                        ? Map.of()
                        : Collections.unmodifiableMap(
                        new HashMap<>(
                                metadata
                        )
                );
    }

    public boolean isSync() {
        return sync;
    }

    public long getDelay() {
        return delay;
    }

    public TimeUnit getDelayUnit() {
        return delayUnit;
    }

    public long getRepeatDelay() {
        return repeatDelay;
    }

    public TimeUnit getRepeatUnit() {
        return repeatUnit;
    }

    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public boolean isRepeating() {
        return repeatDelay > 0L;
    }

    public long getDelayNanos() {
        return delayUnit.toNanos(
                delay
        );
    }

    public long getRepeatDelayNanos() {
        return repeatUnit.toNanos(
                repeatDelay
        );
    }

    /**
     * Compatibility with the previous API.
     *
     * For new code prefer getDelayUnit()/getRepeatUnit().
     */
    @Deprecated
    public TimeUnit getTimeUnit() {
        return delayUnit;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder syncBuilder() {
        return new Builder()
                .sync(true);
    }

    public static Builder asyncBuilder() {
        return new Builder()
                .sync(false);
    }

    public static Builder copyOf(
            TaskOptions copy
    ) {
        Builder builder =
                new Builder();

        if (copy == null) {
            return builder;
        }

        return builder
                .sync(
                        copy.sync
                )
                .delay(
                        copy.delay,
                        copy.delayUnit
                )
                .repeatDelay(
                        copy.repeatDelay,
                        copy.repeatUnit
                )
                .repeatMode(
                        copy.repeatMode
                )
                .priority(
                        copy.priority
                )
                .name(
                        copy.name
                )
                .metadata(
                        copy.metadata
                );
    }

    public static final class Builder {

        private boolean sync;

        private long delay;

        private TimeUnit delayUnit =
                TimeUnit.MILLISECONDS;

        private long repeatDelay;

        private TimeUnit repeatUnit =
                TimeUnit.MILLISECONDS;

        private RepeatMode repeatMode =
                RepeatMode.FIXED_RATE;

        private TaskPriority priority =
                TaskPriority.NORMAL;

        private String name;

        private Map<String, Object> metadata;

        private Builder() {}

        public Builder sync(
                boolean sync
        ) {
            this.sync =
                    sync;

            return this;
        }

        public Builder delay(
                long delay,
                TimeUnit unit
        ) {
            this.delay =
                    Math.max(
                            0L,
                            delay
                    );

            this.delayUnit =
                    unit == null
                            ? TimeUnit.MILLISECONDS
                            : unit;

            return this;
        }

        public Builder repeatDelay(
                long repeatDelay,
                TimeUnit unit
        ) {
            this.repeatDelay =
                    Math.max(
                            0L,
                            repeatDelay
                    );

            this.repeatUnit =
                    unit == null
                            ? TimeUnit.MILLISECONDS
                            : unit;

            return this;
        }

        public Builder repeatMode(
                RepeatMode repeatMode
        ) {
            this.repeatMode =
                    repeatMode == null
                            ? RepeatMode.FIXED_RATE
                            : repeatMode;

            return this;
        }

        /**
         * Compatibility helper.
         *
         * Sets BOTH units.
         */
        public Builder timeUnit(
                TimeUnit unit
        ) {
            TimeUnit resolved =
                    unit == null
                            ? TimeUnit.MILLISECONDS
                            : unit;

            this.delayUnit =
                    resolved;

            this.repeatUnit =
                    resolved;

            return this;
        }

        public Builder priority(
                TaskPriority priority
        ) {
            this.priority =
                    priority == null
                            ? TaskPriority.NORMAL
                            : priority;

            return this;
        }

        public Builder name(
                String name
        ) {
            this.name =
                    name;

            return this;
        }

        public Builder metadata(
                Map<String, Object> metadata
        ) {
            this.metadata =
                    metadata;

            return this;
        }

        public TaskOptions build() {
            return new TaskOptions(
                    sync,

                    delay,
                    delayUnit,

                    repeatDelay,
                    repeatUnit,

                    repeatMode,

                    priority,

                    name,

                    metadata
            );
        }
    }
}