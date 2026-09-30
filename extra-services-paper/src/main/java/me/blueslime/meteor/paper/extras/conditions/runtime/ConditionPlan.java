package me.blueslime.meteor.paper.extras.conditions.runtime;

import me.blueslime.meteor.paper.extras.runtime.context.ContextKey;
import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;
import me.blueslime.meteor.paper.extras.runtime.exception.MissingExecutionContextException;

import java.util.List;

public final class ConditionPlan {

    public static final ConditionPlan EMPTY =
            new ConditionPlan(
                    ConditionMode.ALL,
                    List.of()
            );

    private final ConditionMode mode;

    private final List<CompiledCondition> conditions;

    public ConditionPlan(
            ConditionMode mode,
            List<CompiledCondition> conditions
    ) {
        this.mode = mode;

        this.conditions =
                List.copyOf(
                        conditions
                );
    }

    public ConditionEvaluation evaluate(
            ExecutionContext context
    ) {
        long started =
                System.nanoTime();

        if (conditions.isEmpty()) {
            return new ConditionEvaluation(
                    ConditionEvaluationStatus.PASSED,
                    mode,
                    null,
                    null,
                    0,
                    System.nanoTime()
                            - started
            );
        }

        int evaluated =
                0;

        CompiledCondition last =
                null;

        for (
                CompiledCondition condition :
                conditions
        ) {
            last = condition;

            try {
                context
                        .cancellation()
                        .throwIfCancelled();

                validateRequirements(
                        context,
                        condition
                );

                boolean result =
                        condition
                                .instruction()
                                .test(context);

                evaluated++;

                if (
                        mode ==
                                ConditionMode.ALL &&
                                !result
                ) {
                    return new ConditionEvaluation(
                            ConditionEvaluationStatus.FAILED,
                            mode,
                            condition,
                            null,
                            evaluated,
                            System.nanoTime()
                                    - started
                    );
                }

                if (
                        mode ==
                                ConditionMode.ANY &&
                                result
                ) {
                    return new ConditionEvaluation(
                            ConditionEvaluationStatus.PASSED,
                            mode,
                            condition,
                            null,
                            evaluated,
                            System.nanoTime()
                                    - started
                    );
                }

            } catch (Throwable throwable) {
                /*
                 * Fail closed.
                 *
                 * A broken condition must never turn
                 * into an implicit true.
                 */
                return new ConditionEvaluation(
                        ConditionEvaluationStatus.ERROR,
                        mode,
                        condition,
                        throwable,
                        evaluated,
                        System.nanoTime()
                                - started
                );
            }
        }

        ConditionEvaluationStatus status =
                mode == ConditionMode.ALL
                        ? ConditionEvaluationStatus.PASSED
                        : ConditionEvaluationStatus.FAILED;

        return new ConditionEvaluation(
                status,
                mode,
                last,
                null,
                evaluated,
                System.nanoTime()
                        - started
        );
    }

    public boolean test(
            ExecutionContext context
    ) {
        return evaluate(
                context
        ).passed();
    }

    private void validateRequirements(
            ExecutionContext context,
            CompiledCondition condition
    ) {
        for (
                ContextKey<?> key :
                condition.requirements()
        ) {
            if (
                    !context.contains(key)
            ) {
                throw new MissingExecutionContextException(
                        key
                );
            }
        }
    }

    public ConditionMode mode() {
        return mode;
    }

    public List<CompiledCondition> conditions() {
        return conditions;
    }

    public boolean isEmpty() {
        return conditions.isEmpty();
    }

    public int size() {
        return conditions.size();
    }
}