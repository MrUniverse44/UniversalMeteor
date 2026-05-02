package me.blueslime.meteor.paper.extras.services.scoreboard.animations;

import me.blueslime.meteor.utilities.text.TextReplacer;

public abstract class ScoreboardAnimation {

    protected final long intervalMs;

    public ScoreboardAnimation(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    /**
     * Gets the current frame of this animation
     *
     * @param replacer for text
     * @return animation frame
     */
    public abstract String getCurrentFrame(TextReplacer replacer);

}
