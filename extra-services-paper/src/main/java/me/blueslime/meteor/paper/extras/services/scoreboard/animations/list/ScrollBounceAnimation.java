package me.blueslime.meteor.paper.extras.services.scoreboard.animations.list;

import me.blueslime.meteor.paper.extras.services.scoreboard.animations.ScoreboardAnimation;
import me.blueslime.meteor.utilities.text.TextReplacer;

public class ScrollBounceAnimation extends ScoreboardAnimation {
    private final String rawText;

    public ScrollBounceAnimation(long intervalMs, String rawText) {
        super(intervalMs);
        this.rawText = rawText;
    }

    @Override
    public String getCurrentFrame(TextReplacer replacer) {
        if (rawText == null || rawText.isEmpty()) return "";

        String text = replacer != null ? replacer.apply(rawText) : rawText;
        int len = text.length();

        if (len <= 1) return text;

        int cycleFrames = (len - 1) * 2;
        int tick = (int) ((System.currentTimeMillis() / intervalMs) % cycleFrames);

        int offset;
        if (tick < len) {
            offset = tick;
        } else {
            offset = cycleFrames - tick;
        }

        return text.substring(offset) + text.substring(0, offset);
    }
}
