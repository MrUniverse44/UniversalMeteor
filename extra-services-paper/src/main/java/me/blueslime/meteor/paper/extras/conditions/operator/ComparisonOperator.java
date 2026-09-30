package me.blueslime.meteor.paper.extras.conditions.operator;

import java.math.BigDecimal;

public enum ComparisonOperator {

    EQUALS {
        @Override
        public boolean test(
                String left,
                String right
        ) {
            BigDecimal leftNumber =
                    number(left);

            BigDecimal rightNumber =
                    number(right);

            if (
                    leftNumber != null &&
                            rightNumber != null
            ) {
                return leftNumber
                        .compareTo(
                                rightNumber
                        ) == 0;
            }

            return safe(left)
                    .equals(
                            safe(right)
                    );
        }
    },

    NOT_EQUALS {
        @Override
        public boolean test(
                String left,
                String right
        ) {
            return !EQUALS.test(
                    left,
                    right
            );
        }
    },

    GREATER_THAN {
        @Override
        public boolean test(
                String left,
                String right
        ) {
            return compare(
                    left,
                    right
            ) > 0;
        }
    },

    GREATER_OR_EQUAL {
        @Override
        public boolean test(
                String left,
                String right
        ) {
            return compare(
                    left,
                    right
            ) >= 0;
        }
    },

    LESS_THAN {
        @Override
        public boolean test(
                String left,
                String right
        ) {
            return compare(
                    left,
                    right
            ) < 0;
        }
    },

    LESS_OR_EQUAL {
        @Override
        public boolean test(
                String left,
                String right
        ) {
            return compare(
                    left,
                    right
            ) <= 0;
        }
    },

    EQUALS_IGNORE_CASE {
        @Override
        public boolean test(
                String left,
                String right
        ) {
            return safe(left)
                    .equalsIgnoreCase(
                            safe(right)
                    );
        }
    },

    STARTS_WITH {
        @Override
        public boolean test(
                String left,
                String right
        ) {
            return safe(left)
                    .startsWith(
                            safe(right)
                    );
        }
    },

    ENDS_WITH {
        @Override
        public boolean test(
                String left,
                String right
        ) {
            return safe(left)
                    .endsWith(
                            safe(right)
                    );
        }
    },

    CONTAINS {
        @Override
        public boolean test(
                String left,
                String right
        ) {
            return safe(left)
                    .contains(
                            safe(right)
                    );
        }
    },

    NOT_CONTAINS {
        @Override
        public boolean test(
                String left,
                String right
        ) {
            return !CONTAINS.test(
                    left,
                    right
            );
        }
    };

    public abstract boolean test(
            String left,
            String right
    );

    private static int compare(
            String left,
            String right
    ) {
        BigDecimal leftNumber =
                number(left);

        BigDecimal rightNumber =
                number(right);

        if (
                leftNumber != null &&
                        rightNumber != null
        ) {
            return leftNumber.compareTo(
                    rightNumber
            );
        }

        return safe(left)
                .compareTo(
                        safe(right)
                );
    }

    private static BigDecimal number(
            String value
    ) {
        if (value == null) {
            return null;
        }

        try {
            return new BigDecimal(
                    value.strip()
            );

        } catch (
                NumberFormatException ignored
        ) {
            return null;
        }
    }

    private static String safe(
            String value
    ) {
        return value == null
                ? ""
                : value;
    }
}