package me.blueslime.meteor.paper.extras.actions.exception;

public class ActionCompileException extends RuntimeException {

    public ActionCompileException(String message) {
        super(message);
    }

    public ActionCompileException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
