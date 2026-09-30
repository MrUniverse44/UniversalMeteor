package me.blueslime.meteor.paper.extras.conditions.registry;

import me.blueslime.meteor.paper.extras.conditions.api.Condition;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ConditionRegistry {

    private final ConcurrentMap<String, Condition> registry =
            new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Condition> primary =
            new ConcurrentHashMap<>();

    public synchronized void register(
            Condition condition
    ) {
        Objects.requireNonNull(
                condition,
                "condition"
        );

        Set<String> keys =
                new LinkedHashSet<>();

        keys.add(
                normalize(
                        condition.id()
                )
        );

        for (
                String alias :
                condition.aliases()
        ) {
            keys.add(
                    normalize(alias)
            );
        }

        for (String key : keys) {
            Condition existing =
                    registry.get(key);

            if (
                    existing != null &&
                            existing != condition
            ) {
                throw new IllegalStateException(
                        "Condition id/alias '"
                                + key
                                + "' is already registered by "
                                + existing
                                .getClass()
                                .getName()
                );
            }
        }

        primary.put(
                normalize(
                        condition.id()
                ),
                condition
        );

        for (String key : keys) {
            registry.put(
                    key,
                    condition
            );
        }
    }

    public synchronized Condition unregister(
            String id
    ) {
        String normalized =
                normalize(id);

        Condition condition =
                primary.remove(
                        normalized
                );

        if (condition == null) {
            condition =
                    registry.get(
                            normalized
                    );

            if (condition != null) {
                primary.remove(
                        normalize(
                                condition.id()
                        )
                );
            }
        }

        if (condition == null) {
            return null;
        }

        Condition target =
                condition;

        registry
                .entrySet()
                .removeIf(
                        entry ->
                                entry.getValue()
                                        == target
                );

        return condition;
    }

    public Optional<Condition> find(
            String id
    ) {
        if (id == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                registry.get(
                        normalize(id)
                )
        );
    }

    public Collection<Condition> conditions() {
        return List.copyOf(
                new LinkedHashSet<>(
                        primary.values()
                )
        );
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