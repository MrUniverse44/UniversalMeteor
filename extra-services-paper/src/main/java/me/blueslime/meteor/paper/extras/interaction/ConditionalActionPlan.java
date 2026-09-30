package me.blueslime.meteor.paper.extras.interaction;

import me.blueslime.meteor.paper.extras.actions.runtime.ActionPlan;
import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionPlan;

import java.util.Objects;

public record ConditionalActionPlan(
        ConditionPlan conditions,
        ActionPlan actions,
        ActionPlan deniedActions
) {

    public ConditionalActionPlan {
        conditions =
                Objects.requireNonNullElse(
                        conditions,
                        ConditionPlan.EMPTY
                );

        actions =
                Objects.requireNonNullElse(
                        actions,
                        ActionPlan.EMPTY
                );

        deniedActions =
                Objects.requireNonNullElse(
                        deniedActions,
                        ActionPlan.EMPTY
                );
    }

    public static ConditionalActionPlan actions(
            ActionPlan actions
    ) {
        return new ConditionalActionPlan(
                ConditionPlan.EMPTY,
                actions,
                ActionPlan.EMPTY
        );
    }

    public static ConditionalActionPlan conditional(
            ConditionPlan conditions,
            ActionPlan actions
    ) {
        return new ConditionalActionPlan(
                conditions,
                actions,
                ActionPlan.EMPTY
        );
    }

    public static ConditionalActionPlan conditional(
            ConditionPlan conditions,
            ActionPlan actions,
            ActionPlan deniedActions
    ) {
        return new ConditionalActionPlan(
                conditions,
                actions,
                deniedActions
        );
    }

    public boolean conditional() {
        return !conditions.isEmpty();
    }

    public boolean hasActions() {
        return !actions.isEmpty();
    }

    public boolean hasDeniedActions() {
        return !deniedActions.isEmpty();
    }

    public boolean empty() {
        return conditions.isEmpty() &&
                actions.isEmpty() &&
                deniedActions.isEmpty();
    }
}
