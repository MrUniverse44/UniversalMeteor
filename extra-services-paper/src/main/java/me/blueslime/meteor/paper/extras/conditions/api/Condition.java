package me.blueslime.meteor.paper.extras.conditions.api;

import me.blueslime.meteor.paper.extras.conditions.compiler.ConditionCompileContext;
import me.blueslime.meteor.paper.extras.conditions.compiler.ConditionNode;
import me.blueslime.meteor.paper.extras.runtime.context.ContextKey;

import java.util.Set;

public interface Condition {

    String id();

    default Set<String> aliases() {
        return Set.of();
    }

    default Set<ContextKey<?>> requirements(
            ConditionNode node
    ) {
        return Set.of();
    }

    ConditionInstruction compile(ConditionNode node, ConditionCompileContext context);
}