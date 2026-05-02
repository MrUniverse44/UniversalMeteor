package me.blueslime.meteor.paper.extras.services.scoreboard.animations.list;

import me.blueslime.meteor.paper.extras.services.scoreboard.animations.ScoreboardAnimation;
import me.blueslime.meteor.utilities.text.TextReplacer;

public class WritingInOutAnimation extends ScoreboardAnimation {
    private final String text;

    public WritingInOutAnimation(long intervalMs, String text) {
        super(intervalMs);
        this.text = text;
    }

    /**
     * Gets the current frame of this animation
     *
     * @param replacer for text
     * @return animation frame
     */
    @Override
    public String getCurrentFrame(TextReplacer replacer) {
        int maxLen = text.length();
        int cycleFrames = maxLen + 2 + maxLen;

        int currentFrame = (int) ((System.currentTimeMillis() / intervalMs) % cycleFrames);

        if (currentFrame <= maxLen) {
            return text.substring(0, currentFrame);
        } else if (currentFrame <= maxLen + 2) {
            return text;
        } else {
            int charsToRemove = currentFrame - (maxLen + 2);
            return text.substring(0, maxLen - charsToRemove);
        }
    }
}
