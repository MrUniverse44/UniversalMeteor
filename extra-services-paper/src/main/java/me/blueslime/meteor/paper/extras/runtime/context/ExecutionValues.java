package me.blueslime.meteor.paper.extras.runtime.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ExecutionValues {

    private final Map<String, Object> values;

    public ExecutionValues(
            Map<String, Object> values
    ) {
        this.values =
                Collections.unmodifiableMap(
                        new HashMap<>(values)
                );
    }

    public boolean contains(
            ContextKey<?> key
    ) {
        Object value =
                values.get(key.id());

        return value != null &&
                key.type().isInstance(value);
    }

    public boolean contains(
            String id
    ) {
        return values.containsKey(
                normalize(id)
        );
    }

    public <T> Optional<T> find(
            ContextKey<T> key
    ) {
        Object value =
                values.get(key.id());

        if (value == null) {
            return Optional.empty();
        }

        if (
                !key.type()
                        .isInstance(value)
        ) {
            throw new IllegalStateException(
                    "Context value '"
                            + key.id()
                            + "' was expected to be "
                            + key.type().getName()
                            + " but contains "
                            + value.getClass().getName()
            );
        }

        return Optional.of(
                key.type().cast(value)
        );
    }

    public <T> T get(
            ContextKey<T> key
    ) {
        return find(key)
                .orElse(null);
    }

    public Optional<Object> findById(
            String id
    ) {
        if (id == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                values.get(
                        normalize(id)
                )
        );
    }

    public Map<String, Object> asMap() {
        return values;
    }

    private String normalize(
            String id
    ) {
        return id
                .strip()
                .toLowerCase(
                        Locale.ROOT
                );
    }
}