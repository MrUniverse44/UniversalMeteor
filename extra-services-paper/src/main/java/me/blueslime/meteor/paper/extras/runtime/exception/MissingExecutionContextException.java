package me.blueslime.meteor.paper.extras.runtime.exception;

import me.blueslime.meteor.paper.extras.runtime.context.ContextKey;

import java.util.Arrays;

public class MissingExecutionContextException extends RuntimeException {

    public MissingExecutionContextException(ContextKey<?>... keys) {
        super("Missing execution context for keys: " + Arrays.asList(keys));
    }

    public MissingExecutionContextException(String message) {
        super(message);
    }

}
