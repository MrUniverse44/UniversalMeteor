package me.blueslime.meteor.paper.extras.runtime.context;

import java.util.Map;

public record ExecutionContextSnapshot(
        Map<String, Object> values,
        Map<String, Object> variables
) {

    public ExecutionContextSnapshot {
        values = Map.copyOf(values);
        variables = Map.copyOf(variables);
    }
}