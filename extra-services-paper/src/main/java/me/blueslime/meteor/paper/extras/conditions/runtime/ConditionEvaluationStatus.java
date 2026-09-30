package me.blueslime.meteor.paper.extras.conditions.runtime;

public enum ConditionEvaluationStatus {

    PASSED,
    FAILED,

    /**
     * Invalid runtime state, missing context,
     * PlaceholderAPI problem, plugin exception, etc.
     *
     * ERROR is always considered a failure.
     */
    ERROR,

    /**
     * ConditionService is overloaded or shutting down.
     *
     * Also considered failure.
     */
    REJECTED
}