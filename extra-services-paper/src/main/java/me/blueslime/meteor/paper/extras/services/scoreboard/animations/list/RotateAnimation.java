package me.blueslime.meteor.paper.extras.services.scoreboard.animations.list;

import me.blueslime.meteor.paper.extras.services.scoreboard.animations.ScoreboardAnimation;
import me.blueslime.meteor.utilities.text.TextReplacer;

import java.util.List;

public class RotateAnimation extends ScoreboardAnimation {
    private final List<String> lines;

    public RotateAnimation(long intervalMs, List<String> lines) {
        super(intervalMs);
        this.lines = lines;
    }

    /**
     * Gets the current frame of this animation
     *
     * @param replacer for text
     * @return animation frame
     */
    @Override
    public String getCurrentFrame(TextReplacer replacer) {
        if (lines.isEmpty()) return "";
        int index = (int) ((System.currentTimeMillis() / intervalMs) % lines.size());
        return replacer != null ? replacer.apply(lines.get(index)) : lines.get(index);
    }
}
