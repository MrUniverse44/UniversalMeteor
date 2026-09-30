package me.blueslime.meteor.paper.extras.animation;

import java.util.UUID;

final class AnimationInstance {

    private final UUID id;

    private final UUID groupId;

    private final String key;

    private final AnimationDefinition definition;

    private final AnimationTarget target;

    private final AnimationHandle handle;

    private int frameIndex;

    private int direction =
            1;

    private long nextTick;

    private boolean cancelled;

    AnimationInstance(
            UUID id,
            UUID groupId,
            String key,
            AnimationDefinition definition,
            AnimationTarget target,
            AnimationHandle handle,
            long currentTick
    ) {
        this.id = id;
        this.groupId = groupId;
        this.key = key;
        this.definition = definition;
        this.target = target;
        this.handle = handle;

        this.nextTick =
                currentTick
                        + definition.initialDelayTicks();
    }

    UUID id() {
        return id;
    }

    UUID groupId() {
        return groupId;
    }

    String key() {
        return key;
    }

    long nextTick() {
        return nextTick;
    }

    boolean cancelled() {
        return cancelled ||
                handle.isCancelled();
    }

    void cancel() {
        cancelled = true;
        handle.markCancelled();
    }

    boolean process(
            long currentTick
    ) {
        if (
                cancelled() ||
                        !target.active()
        ) {
            return false;
        }

        AnimationFrame frame =
                definition.frame(
                        frameIndex
                );

        boolean keep =
                target.apply(
                        frame,
                        frameIndex
                );

        if (!keep) {
            return false;
        }

        long duration =
                frame.durationTicks();

        switch (
                definition.mode()
        ) {
            case LOOP ->
                    frameIndex =
                            (
                                    frameIndex + 1
                            ) %
                                    definition.frameCount();

            case ONCE -> {
                if (
                        frameIndex >=
                                definition.frameCount() - 1
                ) {
                    return false;
                }

                frameIndex++;
            }

            case PING_PONG -> {
                if (
                        definition.frameCount() <= 1
                ) {
                    frameIndex = 0;

                } else {
                    if (
                            frameIndex >=
                                    definition.frameCount() - 1
                    ) {
                        direction = -1;

                    } else if (
                            frameIndex <= 0
                    ) {
                        direction = 1;
                    }

                    frameIndex +=
                            direction;
                }
            }
        }

        nextTick =
                currentTick + duration;

        return true;
    }
}