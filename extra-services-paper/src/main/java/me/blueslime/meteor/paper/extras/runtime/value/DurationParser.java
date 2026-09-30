package me.blueslime.meteor.paper.extras.runtime.value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Locale;

public final class DurationParser {

    private DurationParser() {}

    public static Duration parse(
            String input
    ) {
        if (
                input == null ||
                        input.isBlank()
        ) {
            return Duration.ZERO;
        }

        String normalized =
                input
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        int unitStart =
                findUnitStart(
                        normalized
                );

        String numberPart =
                unitStart < 0
                        ? normalized
                        : normalized.substring(
                        0,
                        unitStart
                );

        String unit =
                unitStart < 0
                        ? "ms"
                        : normalized.substring(
                        unitStart
                );

        BigDecimal value;

        try {
            value =
                    new BigDecimal(
                            numberPart.trim()
                    );

        } catch (
                NumberFormatException exception
        ) {
            throw new IllegalArgumentException(
                    "Invalid duration '"
                            + input
                            + "'",
                    exception
            );
        }

        if (
                value.signum() < 0
        ) {
            throw new IllegalArgumentException(
                    "Duration cannot be negative: "
                            + input
            );
        }

        BigDecimal nanosPerUnit =
                switch (unit) {
                    case "ns" ->
                            BigDecimal.ONE;

                    case "us",
                         "µs" ->
                            BigDecimal.valueOf(
                                    1_000L
                            );

                    case "ms" ->
                            BigDecimal.valueOf(
                                    1_000_000L
                            );

                    case "t",
                         "tick",
                         "ticks" ->
                            BigDecimal.valueOf(
                                    50_000_000L
                            );

                    case "s",
                         "sec",
                         "secs",
                         "second",
                         "seconds" ->
                            BigDecimal.valueOf(
                                    1_000_000_000L
                            );

                    case "m",
                         "min",
                         "mins",
                         "minute",
                         "minutes" ->
                            BigDecimal.valueOf(
                                    60_000_000_000L
                            );

                    case "h",
                         "hour",
                         "hours" ->
                            BigDecimal.valueOf(
                                    3_600_000_000_000L
                            );

                    default ->
                            throw new IllegalArgumentException(
                                    "Unknown duration unit '"
                                            + unit
                                            + "' in '"
                                            + input
                                            + "'"
                            );
                };

        BigDecimal nanos =
                value.multiply(
                        nanosPerUnit
                );

        long resolved;

        try {
            resolved =
                    nanos
                            .setScale(
                                    0,
                                    RoundingMode.HALF_UP
                            )
                            .longValueExact();

        } catch (
                ArithmeticException exception
        ) {
            throw new IllegalArgumentException(
                    "Duration is too large: "
                            + input,
                    exception
            );
        }

        return Duration.ofNanos(
                resolved
        );
    }

    private static int findUnitStart(
            String input
    ) {
        for (
                int index = 0;
                index < input.length();
                index++
        ) {
            char character =
                    input.charAt(
                            index
                    );

            if (
                    Character.isLetter(
                            character
                    ) ||
                            character == 'µ'
            ) {
                return index;
            }
        }

        return -1;
    }
}