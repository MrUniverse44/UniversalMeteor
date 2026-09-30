package me.blueslime.meteor.paper.extras.runtime.context;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ExecutionScope {

    private final Map<String, Object> values =
            new ConcurrentHashMap<>();

    public void set(
            String key,
            Object value
    ) {
        Objects.requireNonNull(
                key,
                "key"
        );

        String normalized =
                normalize(key);

        if (value == null) {
            values.remove(
                    normalized
            );

            return;
        }

        values.put(
                normalized,
                value
        );
    }

    public Object get(
            String key
    ) {
        if (key == null) {
            return null;
        }

        return values.get(
                normalize(key)
        );
    }

    public <T> T get(
            String key,
            Class<T> type
    ) {
        Objects.requireNonNull(
                type,
                "type"
        );

        Object value =
                get(key);

        if (value == null) {
            return null;
        }

        if (!type.isInstance(value)) {
            throw new IllegalStateException(
                    "Scoped value '"
                            + key
                            + "' is "
                            + value.getClass().getName()
                            + ", not "
                            + type.getName()
            );
        }

        return type.cast(
                value
        );
    }

    public <T> Optional<T> find(
            String key,
            Class<T> type
    ) {
        return Optional.ofNullable(
                get(
                        key,
                        type
                )
        );
    }

    public boolean contains(
            String key
    ) {
        return key != null &&
                values.containsKey(
                        normalize(key)
                );
    }

    public Object remove(
            String key
    ) {
        if (key == null) {
            return null;
        }

        return values.remove(
                normalize(key)
        );
    }

    public void clear() {
        values.clear();
    }

    public Map<String, Object> snapshot() {
        return Map.copyOf(
                values
        );
    }

    private String normalize(
            String key
    ) {
        return key
                .trim()
                .toLowerCase(
                        java.util.Locale.ROOT
                );
    }
}