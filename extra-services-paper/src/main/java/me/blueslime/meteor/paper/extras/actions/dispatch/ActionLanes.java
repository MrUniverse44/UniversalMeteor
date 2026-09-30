package me.blueslime.meteor.paper.extras.actions.dispatch;

public final class ActionLanes {

    public static final ExecutionLane PLAYER_INTERACTION =
            ExecutionLane.builder(
                            "player-interaction"
                    )
                    .maxActive(128)
                    .maxStartsPerTick(128)
                    .maxPending(4096)
                    .maxPendingPerKey(4)
                    .serialPerKey(true)
                    .startImmediately(true)
                    .overflow(
                            ActionOverflowPolicy.COALESCE
                    )
                    .build();

    public static final ExecutionLane WORLD_MUTATION =
            ExecutionLane.builder(
                            "world-mutation"
                    )
                    .maxActive(64)
                    .maxStartsPerTick(64)
                    .maxPending(20_000)
                    .maxPendingPerKey(64)
                    .serialPerKey(false)
                    .startImmediately(false)
                    .overflow(
                            ActionOverflowPolicy.REJECT
                    )
                    .build();

    public static final ExecutionLane SYSTEM =
            ExecutionLane.builder("system")
                    .maxActive(256)
                    .maxStartsPerTick(256)
                    .maxPending(8192)
                    .maxPendingPerKey(32)
                    .serialPerKey(false)
                    .startImmediately(true)
                    .overflow(
                            ActionOverflowPolicy.REJECT
                    )
                    .build();

    private ActionLanes() {}
}
