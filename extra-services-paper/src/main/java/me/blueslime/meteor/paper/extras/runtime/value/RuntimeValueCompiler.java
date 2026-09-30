package me.blueslime.meteor.paper.extras.runtime.value;

import me.blueslime.meteor.paper.extras.runtime.RuntimeValue;
import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RuntimeValueCompiler {

    private final RuntimeTextResolver textResolver;

    public RuntimeValueCompiler(
            RuntimeTextResolver textResolver
    ) {
        this.textResolver =
                Objects.requireNonNull(
                        textResolver,
                        "textResolver"
                );
    }

    public RuntimeValue<String> compileString(
            String input
    ) {
        String source =
                input == null
                        ? ""
                        : input;

        CompiledTemplate template =
                CompiledTemplate.compile(
                        source
                );

        boolean externalDynamic =
                source.indexOf('%') >= 0;

        if (
                !template.dynamic() &&
                        !externalDynamic
        ) {
            return context ->
                    source;
        }

        return context -> {
            String intermediate =
                    template.resolve(
                            context
                    );

            if (!externalDynamic) {
                return intermediate;
            }

            return textResolver.resolve(
                    intermediate,
                    context
            );
        };
    }

    public RuntimeValue<Double> compileDouble(
            String input
    ) {
        if (
                input == null ||
                        input.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Numeric expression cannot be empty"
            );
        }

        NumericExpression expression =
                new ExpressionParser(
                        input,
                        textResolver
                ).parse();

        return expression::resolve;
    }

    public RuntimeValue<Double> compileDouble(
            String input,
            double fallback
    ) {
        if (
                input == null ||
                        input.isBlank()
        ) {
            return context ->
                    fallback;
        }

        return compileDouble(
                input
        );
    }

    public RuntimeValue<Integer> compileInt(
            String input
    ) {
        RuntimeValue<Double> compiled =
                compileDouble(
                        input
                );

        return context ->
                (int) Math.round(
                        compiled.resolve(
                                context
                        )
                );
    }

    public RuntimeValue<Integer> compileInt(
            String input,
            int fallback
    ) {
        if (
                input == null ||
                        input.isBlank()
        ) {
            return context ->
                    fallback;
        }

        return compileInt(
                input
        );
    }

    public RuntimeValue<Long> compileLong(
            String input
    ) {
        RuntimeValue<Double> compiled =
                compileDouble(
                        input
                );

        return context ->
                Math.round(
                        compiled.resolve(
                                context
                        )
                );
    }

    public RuntimeValue<Duration> compileDuration(
            String input
    ) {
        if (
                input == null ||
                        input.isBlank()
        ) {
            return context ->
                    Duration.ZERO;
        }

        boolean dynamic =
                input.contains("${") ||
                        input.indexOf('%') >= 0;

        if (!dynamic) {
            Duration constant =
                    DurationParser.parse(
                            input
                    );

            return context ->
                    constant;
        }

        RuntimeValue<String> string =
                compileString(
                        input
                );

        return context ->
                DurationParser.parse(
                        string.resolve(
                                context
                        )
                );
    }

    /*
     * --------------------------------------------------------------------
     * Text template
     * --------------------------------------------------------------------
     */

    private record CompiledTemplate(
            List<TemplatePart> parts,
            boolean dynamic
    ) {

        private static CompiledTemplate compile(
                String source
        ) {
            List<TemplatePart> parts =
                    new ArrayList<>();

            int index =
                    0;

            int literalStart =
                    0;

            boolean dynamic =
                    false;

            while (
                    index < source.length()
            ) {
                if (
                        source.charAt(index) == '$' &&
                                index + 1 < source.length() &&
                                source.charAt(
                                        index + 1
                                ) == '{'
                ) {
                    int end =
                            source.indexOf(
                                    '}',
                                    index + 2
                            );

                    if (end < 0) {
                        throw new IllegalArgumentException(
                                "Missing '}' in runtime variable: "
                                        + source
                        );
                    }

                    if (
                            literalStart <
                                    index
                    ) {
                        parts.add(
                                new LiteralPart(
                                        source.substring(
                                                literalStart,
                                                index
                                        )
                                )
                        );
                    }

                    String key =
                            source.substring(
                                    index + 2,
                                    end
                            ).trim();

                    if (key.isEmpty()) {
                        throw new IllegalArgumentException(
                                "Runtime variable name cannot be empty"
                        );
                    }

                    parts.add(
                            new ScopePart(
                                    key
                            )
                    );

                    dynamic =
                            true;

                    index =
                            end + 1;

                    literalStart =
                            index;

                    continue;
                }

                index++;
            }

            if (
                    literalStart <
                            source.length()
            ) {
                parts.add(
                        new LiteralPart(
                                source.substring(
                                        literalStart
                                )
                        )
                );
            }

            if (parts.isEmpty()) {
                parts =
                        List.of(
                                new LiteralPart(
                                        source
                                )
                        );
            }

            return new CompiledTemplate(
                    List.copyOf(
                            parts
                    ),
                    dynamic
            );
        }

        private String resolve(
                ExecutionContext context
        ) {
            if (!dynamic) {
                return ((LiteralPart) parts.getFirst())
                        .value();
            }

            StringBuilder builder =
                    new StringBuilder();

            for (
                    TemplatePart part :
                    parts
            ) {
                part.append(
                        builder,
                        context
                );
            }

            return builder.toString();
        }
    }

    private sealed interface TemplatePart
            permits LiteralPart, ScopePart {

        void append(
                StringBuilder output,
                ExecutionContext context
        );
    }

    private record LiteralPart(
            String value
    ) implements TemplatePart {

        @Override
        public void append(
                StringBuilder output,
                ExecutionContext context
        ) {
            output.append(
                    value
            );
        }
    }

    private record ScopePart(
            String key
    ) implements TemplatePart {

        @Override
        public void append(
                StringBuilder output,
                ExecutionContext context
        ) {
            Object value =
                    context
                            .scope()
                            .get(
                                    key
                            );

            if (value == null) {
                throw new IllegalStateException(
                        "Runtime variable '${"
                                + key
                                + "}' does not exist"
                );
            }

            output.append(
                    value
            );
        }
    }

    /*
     * --------------------------------------------------------------------
     * Numeric expression
     * --------------------------------------------------------------------
     */

    @FunctionalInterface
    private interface NumericExpression {

        double resolve(
                ExecutionContext context
        ) throws Exception;
    }

    private static final class ExpressionParser {

        private final RuntimeTextResolver textResolver;

        private final String source;

        private int index;

        private ExpressionParser(
                String source,
                RuntimeTextResolver textResolver
        ) {
            this.source =
                    source;

            this.textResolver =
                    textResolver;
        }

        private NumericExpression parse() {
            NumericExpression expression =
                    parseExpression();

            skipWhitespace();

            if (
                    index !=
                            source.length()
            ) {
                throw error(
                        "Unexpected token"
                );
            }

            return expression;
        }

        private NumericExpression parseExpression() {
            NumericExpression left =
                    parseTerm();

            while (true) {
                skipWhitespace();

                if (match('+')) {
                    NumericExpression previous =
                            left;

                    NumericExpression right =
                            parseTerm();

                    left =
                            context ->
                                    previous.resolve(
                                            context
                                    ) +
                                            right.resolve(
                                                    context
                                            );

                    continue;
                }

                if (match('-')) {
                    NumericExpression previous =
                            left;

                    NumericExpression right =
                            parseTerm();

                    left =
                            context ->
                                    previous.resolve(
                                            context
                                    ) -
                                            right.resolve(
                                                    context
                                            );

                    continue;
                }

                return left;
            }
        }

        private NumericExpression parseTerm() {
            NumericExpression left =
                    parseUnary();

            while (true) {
                skipWhitespace();

                if (match('*')) {
                    NumericExpression previous =
                            left;

                    NumericExpression right =
                            parseUnary();

                    left =
                            context ->
                                    previous.resolve(
                                            context
                                    ) *
                                            right.resolve(
                                                    context
                                            );

                    continue;
                }

                if (match('/')) {
                    NumericExpression previous =
                            left;

                    NumericExpression right =
                            parseUnary();

                    left =
                            context -> {
                                double divisor =
                                        right.resolve(
                                                context
                                        );

                                if (
                                        divisor ==
                                                0.0D
                                ) {
                                    throw new ArithmeticException(
                                            "Division by zero in expression '"
                                                    + source
                                                    + "'"
                                    );
                                }

                                return previous.resolve(
                                        context
                                ) / divisor;
                            };

                    continue;
                }

                if (match('%')) {
                    /*
                     * "%" can either be modulo or the beginning
                     * of a placeholder.
                     *
                     * At this point we're parsing an operator,
                     * therefore it is modulo.
                     */
                    NumericExpression previous =
                            left;

                    NumericExpression right =
                            parseUnary();

                    left =
                            context -> {
                                double divisor =
                                        right.resolve(
                                                context
                                        );

                                if (
                                        divisor ==
                                                0.0D
                                ) {
                                    throw new ArithmeticException(
                                            "Modulo by zero in expression '"
                                                    + source
                                                    + "'"
                                    );
                                }

                                return previous.resolve(
                                        context
                                ) % divisor;
                            };

                    continue;
                }

                return left;
            }
        }

        private NumericExpression parseUnary() {
            skipWhitespace();

            if (match('+')) {
                return parseUnary();
            }

            if (match('-')) {
                NumericExpression value =
                        parseUnary();

                return context ->
                        -value.resolve(
                                context
                        );
            }

            return parsePrimary();
        }

        private NumericExpression parsePrimary() {
            skipWhitespace();

            if (match('(')) {
                NumericExpression nested =
                        parseExpression();

                skipWhitespace();

                if (!match(')')) {
                    throw error(
                            "Missing ')'"
                    );
                }

                return nested;
            }

            if (peek('%')) {
                return parsePlaceholder();
            }

            if (
                    peek('$') &&
                            peekNext('{')
            ) {
                return parseScopeVariable();
            }

            if (
                    Character.isDigit(
                            current()
                    ) ||
                            peek('.')
            ) {
                return parseNumber();
            }

            if (
                    startsWithIgnoreCase(
                            "pi"
                    )
            ) {
                index +=
                        2;

                return context ->
                        Math.PI;
            }

            if (
                    startsWithIgnoreCase(
                            "e"
                    )
            ) {
                index++;

                return context ->
                        Math.E;
            }

            throw error(
                    "Expected number, placeholder, variable or '('"
            );
        }

        private NumericExpression parseNumber() {
            int start =
                    index;

            boolean exponent =
                    false;

            while (
                    index <
                            source.length()
            ) {
                char character =
                        source.charAt(
                                index
                        );

                if (
                        Character.isDigit(
                                character
                        ) ||
                                character == '.'
                ) {
                    index++;
                    continue;
                }

                if (
                        (character == 'e' ||
                                character == 'E') &&
                                !exponent
                ) {
                    exponent =
                            true;

                    index++;

                    if (
                            index <
                                    source.length() &&
                                    (
                                            source.charAt(index) == '+' ||
                                                    source.charAt(index) == '-'
                                    )
                    ) {
                        index++;
                    }

                    continue;
                }

                break;
            }

            String value =
                    source.substring(
                            start,
                            index
                    );

            double constant;

            try {
                constant =
                        Double.parseDouble(
                                value
                        );

            } catch (
                    NumberFormatException exception
            ) {
                throw error(
                        "Invalid number '"
                                + value
                                + "'"
                );
            }

            return context ->
                    constant;
        }

        private NumericExpression parsePlaceholder() {
            int start =
                    index;

            index++;

            int end =
                    source.indexOf(
                            '%',
                            index
                    );

            if (end < 0) {
                throw error(
                        "Missing closing '%' for placeholder"
                );
            }

            index =
                    end + 1;

            String placeholder =
                    source.substring(
                            start,
                            index
                    );

            return context -> {
                String resolved =
                        textResolver.resolve(
                                placeholder,
                                context
                        );

                if (
                        resolved == null ||
                                resolved.isBlank()
                ) {
                    throw new IllegalStateException(
                            "Placeholder "
                                    + placeholder
                                    + " resolved to an empty value"
                    );
                }

                try {
                    return Double.parseDouble(
                            resolved.trim()
                    );

                } catch (
                        NumberFormatException exception
                ) {
                    throw new IllegalStateException(
                            "Placeholder "
                                    + placeholder
                                    + " resolved to non-numeric value '"
                                    + resolved
                                    + "'",
                            exception
                    );
                }
            };
        }

        private NumericExpression parseScopeVariable() {
            index +=
                    2;

            int end =
                    source.indexOf(
                            '}',
                            index
                    );

            if (end < 0) {
                throw error(
                        "Missing '}' for runtime variable"
                );
            }

            String key =
                    source.substring(
                            index,
                            end
                    ).trim();

            index =
                    end + 1;

            if (key.isEmpty()) {
                throw error(
                        "Runtime variable name cannot be empty"
                );
            }

            return context -> {
                Object value =
                        context
                                .scope()
                                .get(
                                        key
                                );

                if (value == null) {
                    throw new IllegalStateException(
                            "Runtime variable '${"
                                    + key
                                    + "}' does not exist"
                    );
                }

                if (
                        value instanceof Number number
                ) {
                    return number.doubleValue();
                }

                try {
                    return Double.parseDouble(
                            value
                                    .toString()
                                    .trim()
                    );

                } catch (
                        NumberFormatException exception
                ) {
                    throw new IllegalStateException(
                            "Runtime variable '${"
                                    + key
                                    + "}' is not numeric: "
                                    + value,
                            exception
                    );
                }
            };
        }

        private void skipWhitespace() {
            while (
                    index <
                            source.length() &&
                            Character.isWhitespace(
                                    source.charAt(
                                            index
                                    )
                            )
            ) {
                index++;
            }
        }

        private boolean match(
                char expected
        ) {
            if (
                    index >=
                            source.length() ||
                            source.charAt(
                                    index
                            ) !=
                                    expected
            ) {
                return false;
            }

            index++;

            return true;
        }

        private boolean peek(
                char expected
        ) {
            return index <
                    source.length() &&
                    source.charAt(
                            index
                    ) ==
                            expected;
        }

        private boolean peekNext(
                char expected
        ) {
            return index + 1 <
                    source.length() &&
                    source.charAt(
                            index + 1
                    ) ==
                            expected;
        }

        private char current() {
            if (
                    index >=
                            source.length()
            ) {
                return '\0';
            }

            return source.charAt(
                    index
            );
        }

        private boolean startsWithIgnoreCase(
                String value
        ) {
            return source.regionMatches(
                    true,
                    index,
                    value,
                    0,
                    value.length()
            );
        }

        private IllegalArgumentException error(
                String message
        ) {
            return new IllegalArgumentException(
                    message
                            + " at position "
                            + index
                            + " in expression '"
                            + source
                            + "'"
            );
        }
    }
}