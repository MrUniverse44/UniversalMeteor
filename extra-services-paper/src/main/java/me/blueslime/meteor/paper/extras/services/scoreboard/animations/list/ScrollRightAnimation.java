package me.blueslime.meteor.paper.extras.services.scoreboard.animations.list;

import me.blueslime.meteor.paper.extras.services.scoreboard.animations.ScoreboardAnimation;
import me.blueslime.meteor.utilities.text.TextReplacer;

public class ScrollRightAnimation extends ScoreboardAnimation {
    private final String rawText;

    public ScrollRightAnimation(long intervalMs, String rawText) {
        super(intervalMs);
        this.rawText = rawText;
    }

    @Override
    public String getCurrentFrame(TextReplacer replacer) {
        if (rawText == null || rawText.isEmpty()) return "";

        String text = replacer != null ? replacer.apply(rawText) : rawText;
        int len = text.length();

        if (len <= 1) return text;

        int offset = (int) ((System.currentTimeMillis() / intervalMs) % len);
        int split = len - offset;

        return text.substring(split) + text.substring(0, split);
    }
}
