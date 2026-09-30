package me.blueslime.meteor.paper.extras.actions.exception;

import me.blueslime.meteor.paper.extras.runtime.context.ContextKey;

public class MissingActionContextException
        extends ActionExecutionException {

    public MissingActionContextException(ContextKey<?> key) {
        super(
            "Missing required action context value: '"
            + key.id()
            + "' ("
            + key.type().getSimpleName()
            + ")"
        );
    }
}
