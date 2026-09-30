package me.blueslime.meteor.paper.extras.interaction.runtime;

import me.blueslime.meteor.paper.extras.actions.ActionService;
import me.blueslime.meteor.paper.extras.actions.runtime.ActionPlan;

import me.blueslime.meteor.paper.extras.conditions.ConditionService;
import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionEvaluation;

import me.blueslime.meteor.paper.extras.interaction.ConditionalActionPlan;
import me.blueslime.meteor.paper.extras.interaction.InteractionDefinition;
import me.blueslime.meteor.paper.extras.interaction.InteractionType;
import me.blueslime.meteor.paper.extras.interaction.InteractiveItemDefinition;

import me.blueslime.meteor.paper.extras.runtime.context.ContextKeys;
import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class InteractionExecutor {

    private final ConditionService conditions;
    private final ActionService actions;

    public InteractionExecutor(
            ConditionService conditions,
            ActionService actions
    ) {
        this.conditions =
                conditions;

        this.actions =
                actions;
    }

    public CompletableFuture<Void> execute(
            InteractiveItemDefinition item,
            InteractionType type,
            ExecutionContext context
    ) {
        Optional<InteractionDefinition> optional =
                item.interaction(
                        type
                );

        if (optional.isEmpty()) {
            return CompletableFuture.completedFuture(
                    null
            );
        }

        InteractionDefinition interaction =
                optional.get();

        ConditionalActionPlan execution =
                interaction.execution();

        return conditions
                .evaluate(
                        execution.conditions(),
                        context
                )
                .thenCompose(evaluation ->
                        executeResult(
                                execution,
                                evaluation,
                                context
                        )
                );
    }

    private CompletableFuture<Void> executeResult(
            ConditionalActionPlan execution,
            ConditionEvaluation evaluation,
            ExecutionContext context
    ) {
        context.variables()
                .set(
                        "conditionPassed",
                        evaluation.passed()
                );

        context.variables()
                .set(
                        "conditionStatus",
                        evaluation
                                .status()
                                .name()
                );

        if (
                evaluation.decisiveCondition()
                        != null
        ) {
            context.variables()
                    .set(
                            "failedCondition",
                            evaluation
                                    .decisiveCondition()
                                    .raw()
                    );
        }

        ActionPlan selected =
                evaluation.passed()
                        ? execution.actions()
                        : execution.deniedActions();

        if (selected.isEmpty()) {
            return CompletableFuture.completedFuture(
                    null
            );
        }

        UUID playerId =
                context.require(
                        ContextKeys.PLAYER_ID
                );

        return actions
                .executeForPlayer(
                        selected,
                        context,
                        playerId
                )
                .thenApply(
                        ignored -> null
                );
    }
}
