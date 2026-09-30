package me.blueslime.meteor.paper.extras.animation;

import me.blueslime.meteor.paper.extras.animation.AnimationFrame;
import me.blueslime.meteor.paper.extras.animation.AnimationMode;

import java.util.List;
import java.util.Objects;

public final class AnimationDefinition {

    public static final AnimationDefinition NONE =
            new AnimationDefinition(
                    AnimationMode.LOOP,
                    0L,
                    List.of()
            );

    private final AnimationMode mode;

    private final long initialDelayTicks;

    private final List<AnimationFrame> frames;

    public AnimationDefinition(
            AnimationMode mode,
            long initialDelayTicks,
            List<AnimationFrame> frames
    ) {
        this.mode =
                Objects.requireNonNullElse(
                        mode,
                        AnimationMode.LOOP
                );

        this.initialDelayTicks =
                Math.max(
                        0L,
                        initialDelayTicks
                );

        this.frames =
                frames == null
                        ? List.of()
                        : List.copyOf(frames);
    }

    public AnimationMode mode() {
        return mode;
    }

    public long initialDelayTicks() {
        return initialDelayTicks;
    }

    public List<AnimationFrame> frames() {
        return frames;
    }

    public boolean enabled() {
        return !frames.isEmpty();
    }

    public int frameCount() {
        return frames.size();
    }

    public AnimationFrame frame(
            int index
    ) {
        return frames.get(index);
    }
}