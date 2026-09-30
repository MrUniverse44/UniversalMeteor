package me.blueslime.meteor.paper.extras.runtime.context;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ExecutionVariables {

    private final Map<String, Object> values =
            new ConcurrentHashMap<>();

    public void set(
            String key,
            Object value
    ) {
        if (
                key == null ||
                        key.isBlank()
        ) {
            return;
        }

        if (value == null) {
            values.remove(key);
            return;
        }

        values.put(
                key,
                value
        );
    }

    public void putAll(
            Map<String, ?> input
    ) {
        if (
                input == null ||
                        input.isEmpty()
        ) {
            return;
        }

        for (
                Map.Entry<String, ?> entry :
                input.entrySet()
        ) {
            set(
                    entry.getKey(),
                    entry.getValue()
            );
        }
    }

    public Object get(String key) {
        return values.get(key);
    }

    public String getString(
            String key
    ) {
        Object value =
                values.get(key);

        return value == null
                ? null
                : String.valueOf(value);
    }

    public <T> Optional<T> find(
            String key,
            Class<T> type
    ) {
        Object value =
                values.get(key);

        if (
                value == null ||
                        !type.isInstance(value)
        ) {
            return Optional.empty();
        }

        return Optional.of(
                type.cast(value)
        );
    }

    public boolean contains(
            String key
    ) {
        return values.containsKey(key);
    }

    public void remove(
            String key
    ) {
        values.remove(key);
    }

    public void clear() {
        values.clear();
    }

    public Map<String, Object> snapshot() {
        return Map.copyOf(values);
    }
}