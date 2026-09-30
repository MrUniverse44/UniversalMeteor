package me.blueslime.meteor.paper.extras.item.compiler;

import me.blueslime.meteor.paper.extras.actions.ActionService;
import me.blueslime.meteor.paper.extras.actions.runtime.ActionPlan;

import me.blueslime.meteor.paper.extras.conditions.ConditionService;
import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionMode;
import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionPlan;

import java.util.Collection;
import java.util.Objects;

public final class ItemCompileContext {

    private final ActionService actions;

    private final ConditionService conditions;

    public ItemCompileContext(
            ActionService actions,
            ConditionService conditions
    ) {
        this.actions =
                Objects.requireNonNull(
                        actions,
                        "actions"
                );

        this.conditions =
                Objects.requireNonNull(
                        conditions,
                        "conditions"
                );
    }

    public ActionPlan compileActions(
            Collection<String> source
    ) {
        if (
                source == null ||
                        source.isEmpty()
        ) {
            return ActionPlan.EMPTY;
        }

        return actions.compile(
                source
        );
    }

    public ActionPlan compileActions(
            String source
    ) {
        if (
                source == null ||
                        source.isBlank()
        ) {
            return ActionPlan.EMPTY;
        }

        return actions.compileInline(
                source
        );
    }

    public ConditionPlan compileConditions(
            Collection<String> source
    ) {
        return compileConditions(
                source,
                ConditionMode.ALL
        );
    }

    public ConditionPlan compileConditions(
            Collection<String> source,
            ConditionMode mode
    ) {
        if (
                source == null ||
                        source.isEmpty()
        ) {
            return ConditionPlan.EMPTY;
        }

        return conditions.compile(
                source,
                mode
        );
    }

    public ActionService actions() {
        return actions;
    }

    public ConditionService conditions() {
        return conditions;
    }
}