package me.blueslime.meteor.paper.extras.conditions.list;

import me.blueslime.meteor.paper.extras.conditions.api.Condition;
import me.blueslime.meteor.paper.extras.conditions.api.ConditionInstruction;
import me.blueslime.meteor.paper.extras.conditions.compiler.ConditionCompileContext;
import me.blueslime.meteor.paper.extras.conditions.compiler.ConditionNode;
import me.blueslime.meteor.paper.extras.conditions.exception.ConditionCompileException;

import me.blueslime.meteor.paper.extras.conditions.operator.ComparisonExpression;
import me.blueslime.meteor.paper.extras.conditions.operator.ComparisonOperator;
import me.blueslime.meteor.paper.extras.conditions.operator.ComparisonOperators;

import me.blueslime.meteor.paper.extras.runtime.context.ContextKey;
import me.blueslime.meteor.paper.extras.runtime.context.ContextKeys;
import me.blueslime.meteor.paper.extras.runtime.text.ExecutionTextResolver;

import java.util.Set;

public final class PlaceholderCondition
        implements Condition {

    private final ExecutionTextResolver textResolver;

    public PlaceholderCondition(
            ExecutionTextResolver textResolver
    ) {
        this.textResolver =
                textResolver;
    }

    @Override
    public String id() {
        return "placeholder";
    }

    @Override
    public Set<String> aliases() {
        return Set.of(
                "papi"
        );
    }

    @Override
    public Set<ContextKey<?>> requirements(
            ConditionNode node
    ) {
        return Set.of(
                ContextKeys.PLAYER_ID
        );
    }

    @Override
    public ConditionInstruction compile(
            ConditionNode node,
            ConditionCompileContext context
    ) {
        if (
                !textResolver
                        .isPlaceholderApiEnabled()
        ) {
            throw new ConditionCompileException(
                    "PlaceholderAPI is required by condition: "
                            + node.raw()
            );
        }

        String left =
                node.attribute(
                        "left"
                );

        String operatorRaw =
                node.attribute(
                        "operator"
                );

        String right =
                firstNonBlank(
                        node.attribute(
                                "right"
                        ),
                        node.attribute(
                                "value"
                        )
                );

        ComparisonOperator operator;

        if (
                left == null ||
                        operatorRaw == null ||
                        right == null
        ) {
            ComparisonExpression expression =
                    ComparisonOperators
                            .parseExpression(
                                    node.payload()
                            );

            left =
                    expression.left();

            operator =
                    expression.operator();

            right =
                    expression.right();

        } else {
            operator =
                    ComparisonOperators.parse(
                            operatorRaw
                    );
        }

        final String leftTemplate =
                left;

        final String rightTemplate =
                right;

        final ComparisonOperator finalOperator =
                operator;

        return execution -> {
            String processedLeft =
                    textResolver.resolveContext(
                            execution,
                            leftTemplate
                    );

            String processedRight =
                    textResolver.resolveContext(
                            execution,
                            rightTemplate
                    );

            ResolvedPair resolved =
                    execution.waitSyncPlayer(
                            player -> {
                                /*
                                 * PAPI may theoretically have
                                 * disappeared after compilation.
                                 *
                                 * Fail closed.
                                 */
                                if (
                                        !textResolver
                                                .isPlaceholderApiEnabled()
                                ) {
                                    return null;
                                }

                                return new ResolvedPair(
                                        textResolver
                                                .resolvePlaceholders(
                                                        player,
                                                        processedLeft
                                                ),

                                        textResolver
                                                .resolvePlaceholders(
                                                        player,
                                                        processedRight
                                                )
                                );
                            }
                    );

            if (resolved == null) {
                return false;
            }

            return finalOperator.test(
                    resolved.left(),
                    resolved.right()
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

    private record ResolvedPair(
            String left,
            String right
    ) {}
}