package me.blueslime.meteor.paper.extras.actions.runtime;

import me.blueslime.meteor.paper.extras.actions.api.ActionResult;
import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;
import me.blueslime.meteor.paper.extras.runtime.context.ContextKey;
import me.blueslime.meteor.paper.extras.actions.exception.MissingActionContextException;

import java.util.List;

public final class ActionPlan {

    public static final ActionPlan EMPTY =
            new ActionPlan(List.of());

    private final List<CompiledAction> actions;

    public ActionPlan(
            List<CompiledAction> actions
    ) {
        this.actions = List.copyOf(actions);
    }

    public ActionResult execute(
            ExecutionContext context
    ) throws Exception {

        for (CompiledAction action : actions) {
            context.cancellation()
                    .throwIfCancelled();

            validateRequirements(
                    context,
                    action
            );

            ActionResult result =
                    action.instruction()
                            .execute(context);

            if (result == ActionResult.STOP) {
                return ActionResult.STOP;
            }
        }

        return ActionResult.CONTINUE;
    }

    private void validateRequirements(
            ExecutionContext context,
            CompiledAction action
    ) {
        for (ContextKey<?> requirement :
                action.requirements()) {

            if (!context.contains(requirement)) {
                throw new MissingActionContextException(
                        requirement
                );
            }
        }
    }

    public List<CompiledAction> actions() {
        return actions;
    }

    public int size() {
        return actions.size();
    }

    public boolean isEmpty() {
        return actions.isEmpty();
    }
}
