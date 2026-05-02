package me.blueslime.meteor.paper.extras.services.scoreboard.animations.list;

import me.blueslime.meteor.paper.extras.services.scoreboard.animations.ScoreboardAnimation;
import me.blueslime.meteor.utilities.text.TextReplacer;

public class WritingInAnimation extends ScoreboardAnimation {
    private final String text;

    public WritingInAnimation(long intervalMs, String text) {
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
        int currentLen = (int) ((System.currentTimeMillis() / intervalMs) % (maxLen + 1));
        String finalText = text.substring(0, currentLen);
        return replacer != null ? replacer.apply(finalText) : finalText;
    }
}
