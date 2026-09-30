package me.blueslime.meteor.paper.extras.conditions.list;

import me.blueslime.meteor.paper.extras.conditions.api.Condition;
import me.blueslime.meteor.paper.extras.conditions.api.ConditionInstruction;
import me.blueslime.meteor.paper.extras.conditions.compiler.ConditionCompileContext;
import me.blueslime.meteor.paper.extras.conditions.compiler.ConditionNode;
import me.blueslime.meteor.paper.extras.conditions.exception.ConditionCompileException;

import me.blueslime.meteor.paper.extras.conditions.operator.ComparisonExpression;
import me.blueslime.meteor.paper.extras.conditions.operator.ComparisonOperator;
import me.blueslime.meteor.paper.extras.conditions.operator.ComparisonOperators;

import me.blueslime.meteor.paper.extras.runtime.text.ExecutionTextResolver;

import java.util.Locale;

public final class ContextCondition
        implements Condition {

    private final ExecutionTextResolver textResolver;

    public ContextCondition(
            ExecutionTextResolver textResolver
    ) {
        this.textResolver =
                textResolver;
    }

    @Override
    public String id() {
        return "context";
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

        String operatorRaw =
                node.attribute(
                        "operator"
                );

        String expected =
                firstNonBlank(
                        node.attribute(
                                "value"
                        ),
                        node.attribute(
                                "right"
                        )
                );

        ComparisonOperator operator;

        if (
                key == null ||
                        operatorRaw == null ||
                        expected == null
        ) {
            ComparisonExpression expression =
                    ComparisonOperators
                            .parseExpression(
                                    node.payload()
                            );

            key =
                    expression.left();

            operator =
                    expression.operator();

            expected =
                    expression.right();

        } else {
            operator =
                    ComparisonOperators.parse(
                            operatorRaw
                    );
        }

        if (key.isBlank()) {
            throw new ConditionCompileException(
                    "Context condition requires a key"
            );
        }

        String source =
                node.attribute(
                                "source",
                                "auto"
                        )
                        .strip()
                        .toLowerCase(
                                Locale.ROOT
                        );

        final String finalKey =
                key;

        final String expectedTemplate =
                expected;

        final ComparisonOperator finalOperator =
                operator;

        final String finalSource =
                source;

        return execution -> {
            Object actual =
                    switch (finalSource) {
                        case "variable",
                             "variables" ->
                                execution
                                        .variables()
                                        .get(
                                                finalKey
                                        );

                        case "context",
                             "value",
                             "values" ->
                                execution
                                        .values()
                                        .findById(
                                                finalKey
                                        )
                                        .orElse(null);

                        default -> {
                            Object variable =
                                    execution
                                            .variables()
                                            .get(
                                                    finalKey
                                            );

                            if (variable != null) {
                                yield variable;
                            }

                            yield execution
                                    .values()
                                    .findById(
                                            finalKey
                                    )
                                    .orElse(null);
                        }
                    };

            if (actual == null) {
                return false;
            }

            String expectedValue =
                    textResolver
                            .resolveContext(
                                    execution,
                                    expectedTemplate
                            );

            return finalOperator.test(
                    String.valueOf(
                            actual
                    ),
                    expectedValue
            );
        };
    }

    private String firstNonBlank(
            String first,
            String second
    ) {
        if (
                first != null &&
                        !first.isBlank()
        ) {
            return first;
        }

        if (
                second != null &&
                        !second.isBlank()
        ) {
            return second;
        }

        return null;
    }
}