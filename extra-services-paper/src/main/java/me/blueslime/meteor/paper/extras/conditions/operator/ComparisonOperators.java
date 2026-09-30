package me.blueslime.meteor.paper.extras.conditions.operator;

import me.blueslime.meteor.paper.extras.conditions.exception.ConditionCompileException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ComparisonOperators {

    private static final Map<String, ComparisonOperator>
            OPERATORS =
            new LinkedHashMap<>();

    static {
        /*
         * Longest tokens first.
         */
        register(
            "!*=",
            ComparisonOperator.NOT_EQUALS
        );

        register(
            "=i=",
            ComparisonOperator.EQUALS_IGNORE_CASE
        );

        register(
            ">=",
            ComparisonOperator.GREATER_OR_EQUAL
        );

        register(
            "<=",
            ComparisonOperator.LESS_OR_EQUAL
        );

        register(
            "!=",
            ComparisonOperator.NOT_EQUALS
        );

        register(
            "==",
            ComparisonOperator.EQUALS
        );

        register(
            "|-",
            ComparisonOperator.STARTS_WITH
        );

        register(
            "-|",
            ComparisonOperator.ENDS_WITH
        );

        register(
            "*=",
            ComparisonOperator.CONTAINS
        );

        register(
            ">",
            ComparisonOperator.GREATER_THAN
        );

        register(
            "<",
            ComparisonOperator.LESS_THAN
        );

        /*
         * Friendly aliases.
         */
        register(
            "equals",
            ComparisonOperator.EQUALS
        );

        register(
            "not-equals",
            ComparisonOperator.NOT_EQUALS
        );

        register(
            "contains",
            ComparisonOperator.CONTAINS
        );

        register(
            "not-contains",
            ComparisonOperator.NOT_CONTAINS
        );

        register(
            "starts-with",
            ComparisonOperator.STARTS_WITH
        );

        register(
            "ends-with",
            ComparisonOperator.ENDS_WITH
        );
    }

    private ComparisonOperators() {}

    private static void register(
            String token,
            ComparisonOperator operator
    ) {
        OPERATORS.put(
                token,
                operator
        );
    }

    public static ComparisonOperator parse(
            String input
    ) {
        if (input == null) {
            throw new ConditionCompileException(
                    "Comparison operator cannot be null"
            );
        }

        ComparisonOperator operator =
                OPERATORS.get(
                        input
                                .strip()
                                .toLowerCase(
                                        Locale.ROOT
                                )
                );

        if (operator == null) {
            throw new ConditionCompileException(
                    "Unknown comparison operator '"
                            + input
                            + "'"
            );
        }

        return operator;
    }

    public static ComparisonExpression parseExpression(
            String expression
    ) {
        if (
                expression == null ||
                        expression.isBlank()
        ) {
            throw new ConditionCompileException(
                    "Comparison expression cannot be empty"
            );
        }

        /*
         * Symbolic operators are enough for expressions.
         * Friendly textual aliases are better used
         * through operator="...".
         */
        String[] tokens = {
                "!*=",
                "=i=",
                ">=",
                "<=",
                "!=",
                "==",
                "|-",
                "-|",
                "*=",
                ">",
                "<"
        };

        char quote = 0;

        boolean escaped =
                false;

        for (
                int index = 0;
                index < expression.length();
                index++
        ) {
            char current =
                    expression.charAt(index);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (current == '\\') {
                escaped = true;
                continue;
            }

            if (quote != 0) {
                if (current == quote) {
                    quote = 0;
                }

                continue;
            }

            if (
                    current == '"' ||
                            current == '\''
            ) {
                quote = current;
                continue;
            }

            for (String token : tokens) {
                if (
                        !expression.startsWith(
                                token,
                                index
                        )
                ) {
                    continue;
                }

                String left =
                        expression.substring(
                                0,
                                index
                        ).strip();

                String right =
                        expression.substring(
                                index +
                                        token.length()
                        ).strip();

                if (
                        left.isEmpty() ||
                                right.isEmpty()
                ) {
                    throw new ConditionCompileException(
                            "Invalid comparison expression '"
                                    + expression
                                    + "'"
                    );
                }

                return new ComparisonExpression(
                        left,
                        parse(token),
                        right
                );
            }
        }

        throw new ConditionCompileException(
                "No valid comparison operator found in '"
                        + expression
                        + "'"
        );
    }
}
