package me.blueslime.meteor.paper.extras.conditions.exception;

public class ConditionCompileException
        extends RuntimeException {

    public ConditionCompileException(
            String message
    ) {
        super(message);
    }

    public ConditionCompileException(
            String message,
            Throwable cause
    ) {
        super(
                message,
                cause
        );
    }
}