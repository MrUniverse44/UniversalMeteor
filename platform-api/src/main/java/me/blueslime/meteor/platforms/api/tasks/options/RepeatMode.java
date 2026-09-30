package me.blueslime.meteor.platforms.api.tasks.options;

public enum RepeatMode {

    /**
     * Attempts to preserve the configured temporal rate.
     * <br>
     * If a previous execution is still active, Meteor skips
     * that occurrence instead of overlapping the same task.
     */
    FIXED_RATE,

    /**
     * Waits the configured delay after an execution finishes
     * before starting the next one.
     */
    FIXED_DELAY
}