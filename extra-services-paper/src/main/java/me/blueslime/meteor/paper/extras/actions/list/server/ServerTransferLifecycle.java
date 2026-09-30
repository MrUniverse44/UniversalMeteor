package me.blueslime.meteor.paper.extras.actions.list.server;

import me.blueslime.meteor.paper.extras.actions.runtime.ActionPlan;

import java.util.Objects;

public record ServerTransferLifecycle(
    ActionPlan onJoin,
    ActionPlan onPending,
    ActionPlan onQuit,
    ActionPlan onTransfer
) {

    public ServerTransferLifecycle {
        onJoin = Objects.requireNonNullElse(
                onJoin,
                ActionPlan.EMPTY
        );

        onPending = Objects.requireNonNullElse(
                onPending,
                ActionPlan.EMPTY
        );

        onQuit = Objects.requireNonNullElse(
                onQuit,
                ActionPlan.EMPTY
        );

        onTransfer = Objects.requireNonNullElse(
                onTransfer,
                ActionPlan.EMPTY
        );
    }

    public static ServerTransferLifecycle empty() {
        return new ServerTransferLifecycle(
                ActionPlan.EMPTY,
                ActionPlan.EMPTY,
                ActionPlan.EMPTY,
                ActionPlan.EMPTY
        );
    }
}