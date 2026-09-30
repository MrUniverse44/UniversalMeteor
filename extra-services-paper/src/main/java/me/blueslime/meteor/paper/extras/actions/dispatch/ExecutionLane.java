package me.blueslime.meteor.paper.extras.actions.dispatch;

import java.util.Locale;
import java.util.Objects;

public final class ExecutionLane {

    private final String id;

    private final int maxActive;
    private final int maxStartsPerTick;
    private final int maxPending;
    private final int maxPendingPerKey;

    private final boolean serialPerKey;
    private final boolean startImmediately;

    private final ActionOverflowPolicy overflowPolicy;

    private ExecutionLane(Builder builder) {
        this.id =
                builder.id
                        .trim()
                        .toLowerCase(Locale.ROOT);

        this.maxActive =
                builder.maxActive;

        this.maxStartsPerTick =
                builder.maxStartsPerTick;

        this.maxPending =
                builder.maxPending;

        this.maxPendingPerKey =
                builder.maxPendingPerKey;

        this.serialPerKey =
                builder.serialPerKey;

        this.startImmediately =
                builder.startImmediately;

        this.overflowPolicy =
                builder.overflowPolicy;
    }

    public static Builder builder(
            String id
    ) {
        return new Builder(id);
    }

    public String id() {
        return id;
    }

    public int maxActive() {
        return maxActive;
    }

    public int maxStartsPerTick() {
        return maxStartsPerTick;
    }

    public int maxPending() {
        return maxPending;
    }

    public int maxPendingPerKey() {
        return maxPendingPerKey;
    }

    public boolean serialPerKey() {
        return serialPerKey;
    }

    public boolean startImmediately() {
        return startImmediately;
    }

    public ActionOverflowPolicy overflowPolicy() {
        return overflowPolicy;
    }

    public static final class Builder {

        private final String id;

        private int maxActive = 64;
        private int maxStartsPerTick = 64;

        private int maxPending = 4096;
        private int maxPendingPerKey = 4;

        private boolean serialPerKey;
        private boolean startImmediately = true;

        private ActionOverflowPolicy overflowPolicy =
                ActionOverflowPolicy.REJECT;

        private Builder(String id) {
            this.id =
                    Objects.requireNonNull(
                            id,
                            "id"
                    );
        }

        public Builder maxActive(int value) {
            this.maxActive = value;
            return this;
        }

        public Builder maxStartsPerTick(
                int value
        ) {
            this.maxStartsPerTick = value;
            return this;
        }

        public Builder maxPending(int value) {
            this.maxPending = value;
            return this;
        }

        public Builder maxPendingPerKey(
                int value
        ) {
            this.maxPendingPerKey = value;
            return this;
        }

        public Builder serialPerKey(
                boolean value
        ) {
            this.serialPerKey = value;
            return this;
        }

        public Builder startImmediately(
                boolean value
        ) {
            this.startImmediately = value;
            return this;
        }

        public Builder overflow(
                ActionOverflowPolicy policy
        ) {
            this.overflowPolicy =
                    Objects.requireNonNull(
                            policy,
                            "policy"
                    );

            return this;
        }

        public ExecutionLane build() {
            if (id.isBlank()) {
                throw new IllegalArgumentException(
                        "Lane id cannot be empty"
                );
            }

            if (
                    maxActive <= 0 ||
                            maxStartsPerTick <= 0 ||
                            maxPending <= 0 ||
                            maxPendingPerKey <= 0
            ) {
                throw new IllegalArgumentException(
                        "ExecutionLane limits must be > 0"
                );
            }

            return new ExecutionLane(this);
        }
    }
}
