package me.blueslime.meteor.paper.extras.conditions.runtime;

import me.blueslime.meteor.paper.extras.conditions.api.ConditionInstruction;
import me.blueslime.meteor.paper.extras.runtime.context.ContextKey;

import java.util.Set;

public record CompiledCondition(
        String id,
        String raw,
        Set<ContextKey<?>> requirements,
        ConditionInstruction instruction
) {

    public CompiledCondition {
        requirements =
                Set.copyOf(
                        requirements
                );
    }
}
