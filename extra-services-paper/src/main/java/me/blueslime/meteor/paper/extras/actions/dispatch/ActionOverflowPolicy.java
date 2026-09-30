package me.blueslime.meteor.paper.extras.actions.dispatch;

public enum ActionOverflowPolicy {
    REJECT,
    DROP_NEWEST,
    DROP_OLDEST,
    COALESCE
}
