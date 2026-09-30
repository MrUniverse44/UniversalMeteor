package me.blueslime.meteor.paper.extras.animation;

import java.util.Locale;

public enum AnimationMode {

    LOOP,
    ONCE,
    PING_PONG;

    public static AnimationMode parse(
            String input
    ) {
        if (
                input == null ||
                        input.isBlank()
        ) {
            return LOOP;
        }

        return switch (
                input.strip()
                        .toLowerCase(Locale.ROOT)
                        .replace('_', '-')
                ) {
            case "loop",
                 "repeat" ->
                    LOOP;

            case "once",
                 "single" ->
                    ONCE;

            case "ping-pong",
                 "pingpong",
                 "reverse" ->
                    PING_PONG;

            default ->
                    throw new IllegalArgumentException(
                            "Unknown animation mode '"
                                    + input
                                    + "'"
                    );
        };
    }
}
