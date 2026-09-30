package me.blueslime.meteor.paper.extras.actions.compiler;

import me.blueslime.meteor.paper.extras.actions.runtime.ActionPlan;

import me.blueslime.meteor.paper.extras.runtime.value.RuntimeValueCompiler;

import java.util.Collection;
import java.util.Objects;

public final class ActionCompileContext {

    private final ActionCompiler compiler;

    private final RuntimeValueCompiler values;

    ActionCompileContext(
            ActionCompiler compiler,
            RuntimeValueCompiler values
    ) {
        this.compiler =
                Objects.requireNonNull(
                        compiler,
                        "compiler"
                );

        this.values =
                Objects.requireNonNull(
                        values,
                        "values"
                );
    }

    public ActionPlan compile(
            Collection<String> actions
    ) {
        return compiler.compile(
                actions
        );
    }

    public ActionPlan compile(
            String action
    ) {
        return compiler.compile(
                action
        );
    }

    public ActionPlan compileInline(
            String input
    ) {
        return compiler.compileInline(
                input
        );
    }

    public RuntimeValueCompiler values() {
        return values;
    }
}