package me.blueslime.meteor.paper.extras.runtime.context;

import java.util.Locale;
import java.util.Objects;

public record ContextKey<T>(String id, Class<T> type) {

    public ContextKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");

        id = normalize(id);

        if (id.isEmpty()) {
            throw new IllegalArgumentException("ActionKey id cannot be empty");
        }
    }

    public static <T> ContextKey<T> of(
            String id,
            Class<T> type
    ) {
        return new ContextKey<>(id, type);
    }

    private static String normalize(
            String value
    ) {
        return value
                .strip()
                .toLowerCase(
                        Locale.ROOT
                );
    }
}
