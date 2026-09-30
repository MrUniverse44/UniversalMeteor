package me.blueslime.meteor.paper.extras.actions.dispatch;

import java.util.Objects;

public record ActionExecutionOptions(
        String laneId,
        String serializationKey,
        String coalesceKey
) {

    public ActionExecutionOptions {
        Objects.requireNonNull(
                laneId,
                "laneId"
        );
    }

    public static ActionExecutionOptions lane(
            ExecutionLane lane
    ) {
        return new ActionExecutionOptions(
                lane.id(),
                null,
                null
        );
    }

    public ActionExecutionOptions serializedBy(
            String key
    ) {
        return new ActionExecutionOptions(
                laneId,
                key,
                coalesceKey
        );
    }

    public ActionExecutionOptions coalesceBy(
            String key
    ) {
        return new ActionExecutionOptions(
                laneId,
                serializationKey,
                key
        );
    }
}
