package me.blueslime.meteor.paper.extras.animation;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AnimationHandle {

    private final UUID id;

    private final UUID groupId;

    private final String key;

    private final AnimationService owner;

    private final AtomicBoolean cancelled =
            new AtomicBoolean(false);

    AnimationHandle(
            UUID id,
            UUID groupId,
            String key,
            AnimationService owner
    ) {
        this.id = id;
        this.groupId = groupId;
        this.key = key;
        this.owner = owner;
    }

    public UUID id() {
        return id;
    }

    public UUID groupId() {
        return groupId;
    }

    public String key() {
        return key;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean cancel() {
        if (
                !cancelled.compareAndSet(
                        false,
                        true
                )
        ) {
            return false;
        }

        owner.cancel(id);

        return true;
    }

    void markCancelled() {
        cancelled.set(true);
    }
}