package me.blueslime.meteor.paper.extras.actions.dispatch;

public enum ActionExecutionStatus {
    SUCCESS,
    STOPPED,
    CANCELLED,
    FAILED,
    REJECTED,
    DROPPED,
    COALESCED
}
