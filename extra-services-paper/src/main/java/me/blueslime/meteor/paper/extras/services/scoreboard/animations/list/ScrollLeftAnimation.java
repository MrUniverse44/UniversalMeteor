package me.blueslime.meteor.paper.extras.services.scoreboard.animations.list;

import me.blueslime.meteor.paper.extras.services.scoreboard.animations.ScoreboardAnimation;
import me.blueslime.meteor.utilities.text.TextReplacer;

public class ScrollLeftAnimation extends ScoreboardAnimation {
    private final String rawText;

    public ScrollLeftAnimation(long intervalMs, String rawText) {
        super(intervalMs);
        this.rawText = rawText;
    }

    /**
     * Gets the current frame of this animation
     *
     * @param replacer for text
     * @return animation frame
     */
    @Override
    public String getCurrentFrame(TextReplacer replacer) {
        if (rawText == null || rawText.isEmpty()) return "";

        String text = replacer != null ? replacer.apply(rawText) : rawText;
        int len = text.length();

        if (len <= 1) return text;

        int offset = (int) ((System.currentTimeMillis() / intervalMs) % len);
        return text.substring(offset) + text.substring(0, offset);
    }
}
