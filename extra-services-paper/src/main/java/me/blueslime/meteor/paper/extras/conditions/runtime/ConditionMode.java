package me.blueslime.meteor.paper.extras.conditions.runtime;

import java.util.Locale;

public enum ConditionMode {

    ALL,
    ANY;

    public static ConditionMode from(
            String value
    ) {
        if (
                value == null ||
                        value.isBlank()
        ) {
            return ALL;
        }

        return switch (
                value.strip()
                        .toLowerCase(
                                Locale.ROOT
                        )
                ) {
            case "any",
                 "or",
                 "one" ->
                    ANY;

            default ->
                    ALL;
        };
    }
}