package me.blueslime.meteor.paper.extras.actions.runtime;

import me.blueslime.meteor.paper.extras.actions.api.ActionInstruction;
import me.blueslime.meteor.paper.extras.runtime.context.ContextKey;

import java.util.Set;

public record CompiledAction(
        String id,
        Set<ContextKey<?>> requirements,
        ActionInstruction instruction
) {

    public CompiledAction {
        requirements = Set.copyOf(requirements);
    }
}
