package me.blueslime.meteor.paper.extras.interaction;

import java.util.Objects;

public record InteractionDefinition(
    InteractionType type,
    ConditionalActionPlan execution
) {

    public InteractionDefinition {
        Objects.requireNonNull(
                type,
                "type"
        );

        execution =
                Objects.requireNonNullElseGet(
                        execution,
                        () ->
                                new ConditionalActionPlan(
                                        null,
                                        null,
                                        null
                                )
                );
    }

    public boolean handles(
            InteractionType interaction
    ) {
        return type == InteractionType.ANY ||
                type == interaction;
    }
}
