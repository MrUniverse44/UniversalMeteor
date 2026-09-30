package me.blueslime.meteor.paper.extras.actions.compiler;

import me.blueslime.meteor.paper.extras.actions.runtime.ActionPlan;

import java.util.Collection;

public final class ActionCompileContext {

    private final ActionCompiler compiler;

    ActionCompileContext(
            ActionCompiler compiler
    ) {
        this.compiler = compiler;
    }

    public ActionPlan compile(
            Collection<String> actions
    ) {
        return compiler.compile(actions);
    }

    public ActionPlan compileInline(
            String input
    ) {
        return compiler.compileInline(input);
    }
}
