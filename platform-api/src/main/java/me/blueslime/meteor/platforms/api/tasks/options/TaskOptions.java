package me.blueslime.meteor.platforms.api.tasks.options;

import me.blueslime.meteor.platforms.api.tasks.priority.TaskPriority;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Immutable scheduling options with a builder. Use TaskOptions.builder() or<br>
 * the provided syncBuilder()/asyncBuilder() shortcuts to create options.
 */
public final class TaskOptions {

    private final boolean sync;
    private final long delay;
    private final long repeatDelay;
    private final TimeUnit timeUnit;
    private final TaskPriority priority;
    private final String name;
    private final Map<String, Object> metadata;

    private TaskOptions(boolean sync, long delay, long repeatDelay, TimeUnit timeUnit, TaskPriority priority, String name, Map<String, Object> metadata) {
        this.sync = sync;
        this.delay = delay;
        this.repeatDelay = repeatDelay;
        this.timeUnit = timeUnit;
        this.priority = priority;
        this.name = name;
        this.metadata = metadata == null ? Collections.emptyMap() : Collections.unmodifiableMap(metadata);
    }

    public boolean isSync() {
        return sync;
    }

    public long getDelay() {
        return delay;
    }

    public long getRepeatDelay() {
        return repeatDelay;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
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

    /**
     * Create a new Builder.
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convenience: builder preconfigured for synchronous immediate execution.
     *
     * @return builder
     */
    public static Builder syncBuilder() {
        return new Builder().sync(true);
    }

    /**
     * Convenience: builder preconfigured for asynchronous immediate execution.
     *
     * @return builder
     */
    public static Builder asyncBuilder() {
        return new Builder().sync(false);
    }

    /**
     * Create a Builder initialized from an existing options instance (copy-on-write).
     *
     * @param copy source
     * @return builder prefilled
     */
    public static Builder copyOf(TaskOptions copy) {
        Builder b = new Builder();
        if (copy == null) return b;

        return b.sync(copy.sync)
            .delay(copy.delay, copy.timeUnit)
            .repeatDelay(copy.repeatDelay, copy.timeUnit)
            .timeUnit(copy.timeUnit)
            .priority(copy.priority)
            .name(copy.name)
            .metadata(copy.metadata);
    }

    /**
     * Builder for TaskOptions.
     */
    public static final class Builder {
        private boolean sync = false;
        private long delay = 0L;
        private long repeatDelay = 0L;
        private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
        private TaskPriority priority = TaskPriority.NORMAL;
        private String name = null;
        private Map<String, Object> metadata = null;

        private Builder() {}

        public Builder sync(boolean sync) {
            this.sync = sync;
            return this;
        }

        public Builder delay(long delay, TimeUnit unit) {
            this.delay = Math.max(0, delay);
            this.timeUnit = unit == null ? TimeUnit.MILLISECONDS : unit;
            return this;
        }

        public Builder repeatDelay(long repeatDelay, TimeUnit unit) {
            this.repeatDelay = Math.max(0, repeatDelay);
            this.timeUnit = unit == null ? TimeUnit.MILLISECONDS : unit;
            return this;
        }

        public Builder timeUnit(TimeUnit unit) {
            this.timeUnit = unit == null ? TimeUnit.MILLISECONDS : unit;
            return this;
        }

        public Builder priority(TaskPriority priority) {
            this.priority = priority == null ? TaskPriority.NORMAL : priority;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public TaskOptions build() {
            return new TaskOptions(sync, delay, repeatDelay, timeUnit == null ? TimeUnit.MILLISECONDS : timeUnit, priority == null ? TaskPriority.NORMAL : priority, name, metadata);
        }
    }
}
