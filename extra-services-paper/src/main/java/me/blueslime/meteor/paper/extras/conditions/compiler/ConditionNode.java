package me.blueslime.meteor.paper.extras.conditions.compiler;

import java.util.Locale;
import java.util.Map;

public record ConditionNode(
        String type,
        Map<String, String> attributes,
        String payload,
        String raw,
        boolean negated
) {

    public ConditionNode {
        type =
                normalize(type);

        attributes =
                Map.copyOf(
                        attributes
                );

        payload =
                payload == null
                        ? ""
                        : payload;

        raw =
                raw == null
                        ? ""
                        : raw;
    }

    public String attribute(
            String key
    ) {
        return attributes.get(
                normalizeAttribute(
                        key
                )
        );
    }

    public String attribute(
            String key,
            String fallback
    ) {
        String value =
                attribute(key);

        return value == null
                ? fallback
                : value;
    }

    public boolean booleanAttribute(
            String key,
            boolean fallback
    ) {
        String value =
                attribute(key);

        return value == null
                ? fallback
                : Boolean.parseBoolean(
                value
        );
    }

    static String normalize(
            String value
    ) {
        return value
                .strip()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    static String normalizeAttribute(
            String input
    ) {
        StringBuilder result =
                new StringBuilder();

        for (
                int index = 0;
                index < input.length();
                index++
        ) {
            char current =
                    input.charAt(index);

            if (
                    Character.isUpperCase(
                            current
                    )
            ) {
                if (
                        !result.isEmpty() &&
                                result.charAt(
                                        result.length() - 1
                                ) != '-'
                ) {
                    result.append('-');
                }

                result.append(
                        Character.toLowerCase(
                                current
                        )
                );

            } else if (
                    current == '_' ||
                            current == ' '
            ) {
                result.append('-');

            } else {
                result.append(
                        Character.toLowerCase(
                                current
                        )
                );
            }
        }

        return result.toString();
    }
}