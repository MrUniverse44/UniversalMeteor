package me.blueslime.meteor.paper.extras.actions.dispatch;

public record ActionExecutionResult(
        ActionExecutionStatus status,
        Throwable error
) {

    public static ActionExecutionResult success() {
        return new ActionExecutionResult(
                ActionExecutionStatus.SUCCESS,
                null
        );
    }

    public static ActionExecutionResult stopped() {
        return new ActionExecutionResult(
                ActionExecutionStatus.STOPPED,
                null
        );
    }

    public static ActionExecutionResult cancelled() {
        return new ActionExecutionResult(
                ActionExecutionStatus.CANCELLED,
                null
        );
    }

    public static ActionExecutionResult failed(
            Throwable error
    ) {
        return new ActionExecutionResult(
                ActionExecutionStatus.FAILED,
                error
        );
    }

    public static ActionExecutionResult rejected() {
        return new ActionExecutionResult(
                ActionExecutionStatus.REJECTED,
                null
        );
    }

    public static ActionExecutionResult dropped() {
        return new ActionExecutionResult(
                ActionExecutionStatus.DROPPED,
                null
        );
    }

    public static ActionExecutionResult coalesced() {
        return new ActionExecutionResult(
                ActionExecutionStatus.COALESCED,
                null
        );
    }
}
