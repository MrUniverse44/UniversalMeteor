package me.blueslime.meteor.paper.extras.conditions.compiler;

import me.blueslime.meteor.paper.extras.conditions.exception.ConditionCompileException;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ConditionParser {

    public ConditionNode parse(
            String input
    ) {
        if (
                input == null ||
                        input.isBlank()
        ) {
            throw new ConditionCompileException(
                    "Condition cannot be empty"
            );
        }

        String raw =
                input.strip();

        boolean negated =
                false;

        if (raw.startsWith("!")) {
            negated = true;

            raw =
                    raw.substring(1)
                            .strip();
        }

        if (raw.startsWith("[")) {
            return parseDelimited(
                    input,
                    raw,
                    ']',
                    negated
            );
        }

        if (raw.startsWith("<")) {
            return parseDelimited(
                    input,
                    raw,
                    '>',
                    negated
            );
        }

        return parseLegacy(
                input,
                raw,
                negated
        );
    }

    private ConditionNode parseDelimited(
            String original,
            String input,
            char endToken,
            boolean negated
    ) {
        int end =
                findHeaderEnd(
                        input,
                        endToken
                );

        if (end < 0) {
            throw new ConditionCompileException(
                    "Missing '"
                            + endToken
                            + "' in condition: "
                            + original
            );
        }

        String header =
                input.substring(
                        1,
                        end
                ).strip();

        String payload =
                input.substring(
                        end + 1
                ).strip();

        if (header.isEmpty()) {
            throw new ConditionCompileException(
                    "Missing condition type: "
                            + original
            );
        }

        int typeEnd =
                findFirstWhitespace(
                        header
                );

        String type;
        String attributes;

        if (typeEnd < 0) {
            type =
                    header;

            attributes =
                    "";

        } else {
            type =
                    header.substring(
                            0,
                            typeEnd
                    );

            attributes =
                    header.substring(
                            typeEnd + 1
                    );
        }

        return new ConditionNode(
                type,
                parseAttributes(
                        attributes
                ),
                payload,
                original,
                negated
        );
    }

    private ConditionNode parseLegacy(
            String original,
            String input,
            boolean negated
    ) {
        int separator =
                input.indexOf(':');

        if (separator <= 0) {
            separator =
                    input.indexOf('=');
        }

        if (separator <= 0) {
            throw new ConditionCompileException(
                    "Invalid condition syntax: "
                            + original
            );
        }

        return new ConditionNode(
                input.substring(
                        0,
                        separator
                ),
                Map.of(),
                input.substring(
                        separator + 1
                ).strip(),
                original,
                negated
        );
    }

    private Map<String, String> parseAttributes(
            String input
    ) {
        Map<String, String> result =
                new LinkedHashMap<>();

        int index = 0;

        while (
                index < input.length()
        ) {
            while (
                    index < input.length() &&
                            Character.isWhitespace(
                                    input.charAt(index)
                            )
            ) {
                index++;
            }

            if (
                    index >= input.length()
            ) {
                break;
            }

            int keyStart =
                    index;

            while (
                    index < input.length() &&
                            !Character.isWhitespace(
                                    input.charAt(index)
                            ) &&
                            input.charAt(index)
                                    != '='
            ) {
                index++;
            }

            String key =
                    input.substring(
                            keyStart,
                            index
                    );

            while (
                    index < input.length() &&
                            Character.isWhitespace(
                                    input.charAt(index)
                            )
            ) {
                index++;
            }

            if (
                    index >= input.length() ||
                            input.charAt(index)
                                    != '='
            ) {
                result.put(
                        ConditionNode
                                .normalizeAttribute(
                                        key
                                ),
                        "true"
                );

                continue;
            }

            index++;

            while (
                    index < input.length() &&
                            Character.isWhitespace(
                                    input.charAt(index)
                            )
            ) {
                index++;
            }

            if (
                    index >= input.length()
            ) {
                result.put(
                        ConditionNode
                                .normalizeAttribute(
                                        key
                                ),
                        ""
                );

                break;
            }

            String value;

            char first =
                    input.charAt(index);

            if (
                    first == '"' ||
                            first == '\''
            ) {
                char quote =
                        first;

                index++;

                StringBuilder builder =
                        new StringBuilder();

                boolean escaped =
                        false;

                while (
                        index <
                                input.length()
                ) {
                    char current =
                            input.charAt(
                                    index++
                            );

                    if (escaped) {
                        builder.append(
                                current
                        );

                        escaped =
                                false;

                        continue;
                    }

                    if (
                            current == '\\'
                    ) {
                        escaped = true;
                        continue;
                    }

                    if (
                            current == quote
                    ) {
                        break;
                    }

                    builder.append(
                            current
                    );
                }

                value =
                        builder.toString();

            } else {
                int valueStart =
                        index;

                while (
                        index < input.length() &&
                                !Character.isWhitespace(
                                        input.charAt(index)
                                )
                ) {
                    index++;
                }

                value =
                        input.substring(
                                valueStart,
                                index
                        );
            }

            result.put(
                    ConditionNode
                            .normalizeAttribute(
                                    key
                            ),
                    value
            );
        }

        return result;
    }

    private int findHeaderEnd(
            String input,
            char endToken
    ) {
        char quote = 0;

        boolean escaped =
                false;

        for (
                int index = 1;
                index < input.length();
                index++
        ) {
            char current =
                    input.charAt(index);

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

            if (
                    current == endToken
            ) {
                return index;
            }
        }

        return -1;
    }

    private int findFirstWhitespace(
            String input
    ) {
        for (
                int index = 0;
                index < input.length();
                index++
        ) {
            if (
                    Character.isWhitespace(
                            input.charAt(index)
                    )
            ) {
                return index;
            }
        }

        return -1;
    }
}