package me.blueslime.meteor.paper.extras.actions.compiler.decorator;

import me.blueslime.meteor.paper.extras.actions.api.ActionInstruction;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionNode;

import java.util.List;
import java.util.Objects;

public final class ActionInstructionDecorators {

    private final List<ActionInstructionDecorator> decorators;

    public ActionInstructionDecorators(
            List<ActionInstructionDecorator> decorators
    ) {
        this.decorators =
                List.copyOf(
                        Objects.requireNonNull(
                                decorators,
                                "decorators"
                        )
                );
    }

    public ActionInstruction decorate(
            ActionNode node,
            ActionInstruction instruction
    ) {
        Objects.requireNonNull(
                node,
                "node"
        );

        ActionInstruction current =
                Objects.requireNonNull(
                        instruction,
                        "instruction"
                );

        for (
                ActionInstructionDecorator decorator :
                decorators
        ) {
            current =
                    Objects.requireNonNull(
                            decorator.decorate(
                                    node,
                                    current
                            ),
                            "Decorated ActionInstruction"
                    );
        }

        return current;
    }
}