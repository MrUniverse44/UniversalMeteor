package me.blueslime.meteor.paper.extras.actions.exception;

public class ActionExecutionException extends RuntimeException {

    public ActionExecutionException(String message) {
        super(message);
    }

    public ActionExecutionException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }

    public ActionExecutionException(Throwable cause) {
        super(cause);
    }
}
