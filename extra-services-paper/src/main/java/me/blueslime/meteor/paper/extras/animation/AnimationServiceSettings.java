package me.blueslime.meteor.paper.extras.animation;

import java.time.Duration;
import java.util.Objects;

public final class AnimationServiceSettings {

    private int maxUpdatesPerTick =
            256;

    private int maxCommandsPerTick =
            1024;

    private Duration tickBudget =
            Duration.ofMillis(2);

    private AnimationServiceSettings() {}

    public static AnimationServiceSettings builder() {
        return new AnimationServiceSettings();
    }

    public AnimationServiceSettings maxUpdatesPerTick(
            int value
    ) {
        this.maxUpdatesPerTick =
                value;

        return this;
    }

    public AnimationServiceSettings maxCommandsPerTick(
            int value
    ) {
        this.maxCommandsPerTick =
                value;

        return this;
    }

    public AnimationServiceSettings tickBudget(
            Duration value
    ) {
        this.tickBudget =
                Objects.requireNonNull(value);

        return this;
    }

    public int maxUpdatesPerTick() {
        return maxUpdatesPerTick;
    }

    public int maxCommandsPerTick() {
        return maxCommandsPerTick;
    }

    public Duration tickBudget() {
        return tickBudget;
    }

    public AnimationServiceSettings validate() {
        if (
                maxUpdatesPerTick <= 0 ||
                        maxCommandsPerTick <= 0
        ) {
            throw new IllegalArgumentException(
                    "Animation limits must be > 0"
            );
        }

        if (
                tickBudget.isZero() ||
                        tickBudget.isNegative()
        ) {
            throw new IllegalArgumentException(
                    "tickBudget must be > 0"
            );
        }

        return this;
    }
}