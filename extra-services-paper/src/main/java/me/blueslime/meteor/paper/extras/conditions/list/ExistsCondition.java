package me.blueslime.meteor.paper.extras.conditions.list;

import me.blueslime.meteor.paper.extras.conditions.api.Condition;
import me.blueslime.meteor.paper.extras.conditions.api.ConditionInstruction;
import me.blueslime.meteor.paper.extras.conditions.compiler.ConditionCompileContext;
import me.blueslime.meteor.paper.extras.conditions.compiler.ConditionNode;
import me.blueslime.meteor.paper.extras.conditions.exception.ConditionCompileException;

public final class ExistsCondition
        implements Condition {

    @Override
    public String id() {
        return "exists";
    }

    @Override
    public ConditionInstruction compile(
            ConditionNode node,
            ConditionCompileContext context
    ) {
        String key =
                node.attribute(
                        "key"
                );

        String variable =
                node.attribute(
                        "variable"
                );

        if (
                key == null &&
                        variable == null
        ) {
            key =
                    node.payload()
                            .strip();
        }

        if (
                (key == null ||
                        key.isBlank()) &&
                        (variable == null ||
                                variable.isBlank())
        ) {
            throw new ConditionCompileException(
                    "Exists condition requires 'key' or 'variable'"
            );
        }

        final String finalKey =
                key;

        final String finalVariable =
                variable;

        return execution -> {
            if (
                    finalVariable != null &&
                            !finalVariable.isBlank()
            ) {
                return execution
                        .variables()
                        .contains(
                                finalVariable
                        );
            }

            return execution
                    .values()
                    .contains(
                            finalKey
                    );
        };
    }
}
