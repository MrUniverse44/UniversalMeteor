package me.blueslime.meteor.paper.extras.conditions.compiler;

import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionMode;
import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionPlan;

import java.util.Collection;

public final class ConditionCompileContext {

    private final ConditionCompiler compiler;

    ConditionCompileContext(
            ConditionCompiler compiler
    ) {
        this.compiler =
                compiler;
    }

    public ConditionPlan compile(
            Collection<String> conditions
    ) {
        return compiler.compile(
                conditions
        );
    }

    public ConditionPlan compile(
            Collection<String> conditions,
            ConditionMode mode
    ) {
        return compiler.compile(
                conditions,
                mode
        );
    }

    public ConditionPlan compile(
            String condition
    ) {
        return compiler.compile(
                condition
        );
    }
}