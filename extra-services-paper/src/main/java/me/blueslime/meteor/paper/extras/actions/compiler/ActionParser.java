package me.blueslime.meteor.paper.extras.actions.compiler;

import me.blueslime.meteor.paper.extras.actions.exception.ActionCompileException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ActionParser {

    public ActionNode parse(String input) {
        if (input == null || input.isBlank()) {
            throw new ActionCompileException(
                    "Action cannot be empty"
            );
        }

        String raw = input.trim();

        if (raw.startsWith("<")) {
            return parseDelimited(
                    raw,
                    '<',
                    '>'
            );
        }

        if (raw.startsWith("[")) {
            return parseDelimited(
                    raw,
                    '[',
                    ']'
            );
        }

        return parseLegacy(raw);
    }

    private ActionNode parseDelimited(
            String raw,
            char startToken,
            char endToken
    ) {
        int end = findHeaderEnd(
                raw,
                endToken
        );

        if (end < 0) {
            throw new ActionCompileException(
                    "Missing '"
                            + endToken
                            + "' in action: "
                            + raw
            );
        }

        String header = raw.substring(
                1,
                end
        ).trim();

        String payload = raw.substring(
                end + 1
        );

        if (header.isEmpty()) {
            throw new ActionCompileException(
                    "Missing action type: "
                            + raw
            );
        }

        int typeEnd =
                findFirstWhitespace(header);

        String type;
        String attributesRaw;

        if (typeEnd < 0) {
            type = header;
            attributesRaw = "";
        } else {
            type = header.substring(
                    0,
                    typeEnd
            );

            attributesRaw = header.substring(
                    typeEnd + 1
            );
        }

        return new ActionNode(
                type,
                parseAttributes(
                        attributesRaw
                ),
                payload,
                raw
        );
    }

    private ActionNode parseLegacy(
            String raw
    ) {
        int separator =
                findLegacySeparator(raw);

        if (separator <= 0) {
            throw new ActionCompileException(
                    "Invalid action syntax: "
                            + raw
            );
        }

        String type = raw.substring(
                0,
                separator
        ).trim();

        String payload = raw.substring(
                separator + 1
        );

        return new ActionNode(
                type,
                Map.of(),
                payload,
                raw
        );
    }

    private int findLegacySeparator(
            String input
    ) {
        for (
                int index = 0;
                index < input.length();
                index++
        ) {
            char current =
                    input.charAt(index);

            if (
                    current == ':' ||
                            current == '='
            ) {
                return index;
            }

            /*
             * Don't allow:
             *
             * "message hello"
             *
             * because there's no valid action separator.
             */
            if (Character.isWhitespace(current)) {
                return -1;
            }
        }

        return -1;
    }

    private Map<String, String> parseAttributes(
            String input
    ) {
        Map<String, String> attributes =
                new LinkedHashMap<>();

        int index = 0;

        while (index < input.length()) {
            while (
                    index < input.length() &&
                            Character.isWhitespace(
                                    input.charAt(index)
                            )
            ) {
                index++;
            }

            if (index >= input.length()) {
                break;
            }

            int keyStart = index;

            while (
                    index < input.length() &&
                            !Character.isWhitespace(
                                    input.charAt(index)
                            ) &&
                            input.charAt(index) != '='
            ) {
                index++;
            }

            String key = input.substring(
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

            /*
             * Boolean-like attribute:
             *
             * <something silent>
             */
            if (
                    index >= input.length() ||
                            input.charAt(index) != '='
            ) {
                attributes.put(
                        ActionNode.normalizeAttribute(
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

            if (index >= input.length()) {
                attributes.put(
                        ActionNode.normalizeAttribute(
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
                char quote = first;

                index++;

                StringBuilder builder =
                        new StringBuilder();

                boolean escaped = false;

                while (index < input.length()) {
                    char current =
                            input.charAt(index++);

                    if (escaped) {
                        builder.append(current);
                        escaped = false;
                        continue;
                    }

                    if (current == '\\') {
                        escaped = true;
                        continue;
                    }

                    if (current == quote) {
                        break;
                    }

                    builder.append(current);
                }

                value = builder.toString();

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

                value = input.substring(
                        valueStart,
                        index
                );
            }

            attributes.put(
                    ActionNode.normalizeAttribute(
                            key
                    ),
                    value
            );
        }

        return attributes;
    }

    /**
     * Used for:
     *
     * <message>Hello && <actionbar>World
     *
     * It only considers && a separator when the
     * next expression looks like another action.
     *
     * \&& can be used to escape it.
     */
    public List<String> splitInline(
            String input
    ) {
        List<String> result =
                new ArrayList<>();

        if (input == null || input.isBlank()) {
            return result;
        }

        int start = 0;

        char quote = 0;
        boolean escaped = false;

        for (
                int index = 0;
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
                    current == '&' &&
                            index + 1 < input.length() &&
                            input.charAt(index + 1) == '&' &&
                            looksLikeActionStart(
                                    input,
                                    index + 2
                            )
            ) {
                String action =
                        cleanInline(
                                input.substring(
                                        start,
                                        index
                                ).trim()
                        );

                if (!action.isEmpty()) {
                    result.add(action);
                }

                index++;

                start = index + 1;
            }
        }

        String remaining =
                cleanInline(
                        input.substring(start).trim()
                );

        if (!remaining.isEmpty()) {
            result.add(remaining);
        }

        return result;
    }

    private boolean looksLikeActionStart(
            String input,
            int from
    ) {
        int index = from;

        while (
                index < input.length() &&
                        Character.isWhitespace(
                                input.charAt(index)
                        )
        ) {
            index++;
        }

        if (index >= input.length()) {
            return false;
        }

        char current =
                input.charAt(index);

        if (
                current == '<' ||
                        current == '['
        ) {
            return true;
        }

        int start = index;

        while (
                index < input.length() &&
                        (
                                Character.isLetterOrDigit(
                                        input.charAt(index)
                                ) ||
                                        input.charAt(index) == '-' ||
                                        input.charAt(index) == '_'
                        )
        ) {
            index++;
        }

        return index > start &&
                index < input.length() &&
                (
                        input.charAt(index) == ':' ||
                                input.charAt(index) == '='
                );
    }

    private String cleanInline(
            String input
    ) {
        return input.replace(
                "\\&&",
                "&&"
        );
    }

    private int findHeaderEnd(
            String input,
            char endToken
    ) {
        char quote = 0;
        boolean escaped = false;

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

            if (current == endToken) {
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