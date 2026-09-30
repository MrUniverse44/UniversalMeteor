package me.blueslime.meteor.paper.extras.actions.api;

import me.blueslime.meteor.paper.extras.actions.compiler.ActionCompileContext;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionNode;
import me.blueslime.meteor.paper.extras.runtime.context.ContextKey;

import java.util.Set;

public interface Action {

    String id();

    default Set<String> aliases() {
        return Set.of();
    }

    default Set<ContextKey<?>> requirements(ActionNode node) {
        return Set.of();
    }

    ActionInstruction compile(
        ActionNode node,
        ActionCompileContext context
    );
}
