package me.blueslime.meteor.paper.extras.conditions.runtime;

public record ConditionEvaluation(
        ConditionEvaluationStatus status,
        ConditionMode mode,
        CompiledCondition decisiveCondition,
        Throwable error,
        int evaluatedConditions,
        long durationNanos
) {

    public boolean passed() {
        return status ==
                ConditionEvaluationStatus.PASSED;
    }

    public boolean failed() {
        return !passed();
    }

    public double durationMillis() {
        return durationNanos /
                1_000_000.0D;
    }

    public static ConditionEvaluation rejected(
            ConditionMode mode
    ) {
        return new ConditionEvaluation(
                ConditionEvaluationStatus.REJECTED,
                mode,
                null,
                null,
                0,
                0L
        );
    }
}