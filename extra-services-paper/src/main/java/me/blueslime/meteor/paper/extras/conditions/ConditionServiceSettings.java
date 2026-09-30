package me.blueslime.meteor.paper.extras.conditions;

public final class ConditionServiceSettings {

    private int maxConcurrentEvaluations =
            512;

    private ConditionServiceSettings() {}

    public static ConditionServiceSettings builder() {
        return new ConditionServiceSettings();
    }

    public ConditionServiceSettings maxConcurrentEvaluations(
            int value
    ) {
        this.maxConcurrentEvaluations =
                value;

        return this;
    }

    public int maxConcurrentEvaluations() {
        return maxConcurrentEvaluations;
    }

    public ConditionServiceSettings validate() {
        if (
                maxConcurrentEvaluations <= 0
        ) {
            throw new IllegalArgumentException(
                    "maxConcurrentEvaluations must be > 0"
            );
        }

        return this;
    }
}
