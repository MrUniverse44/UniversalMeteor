package me.blueslime.meteor.paper.extras.actions.compiler;

import java.util.Locale;
import java.util.Map;

public record ActionNode(
        String type,
        Map<String, String> attributes,
        String payload,
        String raw
) {

    public ActionNode {
        type = normalize(type);
        attributes = Map.copyOf(attributes);
        payload = payload == null
                ? ""
                : payload;
    }

    public String attribute(String key) {
        return attributes.get(
                normalizeAttribute(key)
        );
    }

    public String attribute(
            String key,
            String fallback
    ) {
        String result =
                attribute(key);

        return result == null
                ? fallback
                : result;
    }

    public boolean booleanAttribute(
            String key,
            boolean fallback
    ) {
        String value =
                attribute(key);

        if (value == null) {
            return fallback;
        }

        return Boolean.parseBoolean(value);
    }

    public int intAttribute(
            String key,
            int fallback
    ) {
        String value =
                attribute(key);

        if (value == null) {
            return fallback;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static String normalize(String value) {
        return value
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    static String normalizeAttribute(
            String input
    ) {
        StringBuilder result =
                new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            char current =
                    input.charAt(i);

            if (Character.isUpperCase(current)) {
                if (
                        result.length() > 0 &&
                                result.charAt(
                                        result.length() - 1
                                ) != '-'
                ) {
                    result.append('-');
                }

                result.append(
                        Character.toLowerCase(current)
                );
            } else if (
                    current == '_' ||
                            current == ' '
            ) {
                result.append('-');
            } else {
                result.append(
                        Character.toLowerCase(current)
                );
            }
        }

        return result.toString();
    }
}
