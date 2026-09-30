package me.blueslime.meteor.paper.extras.actions.registry;

import me.blueslime.meteor.paper.extras.actions.api.Action;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ActionRegistry {

    private final ConcurrentMap<String, Action> actions =
            new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Action> primary =
            new ConcurrentHashMap<>();

    public synchronized void register(
            Action action
    ) {
        Objects.requireNonNull(
                action,
                "action"
        );

        Set<String> keys =
                new LinkedHashSet<>();

        keys.add(
                normalize(action.id())
        );

        for (String alias :
                action.aliases()) {

            keys.add(
                    normalize(alias)
            );
        }

        for (String key : keys) {
            Action existing =
                    actions.get(key);

            if (
                    existing != null &&
                            existing != action
            ) {
                throw new IllegalStateException(
                        "Action id/alias '"
                                + key
                                + "' is already registered by "
                                + existing.getClass()
                                .getName()
                );
            }
        }

        primary.put(
                normalize(action.id()),
                action
        );

        for (String key : keys) {
            actions.put(
                    key,
                    action
            );
        }
    }

    public synchronized Action unregister(
            String id
    ) {
        String normalized =
                normalize(id);

        Action action =
                primary.remove(normalized);

        if (action == null) {
            action = actions.get(normalized);

            if (action != null) {
                primary.remove(
                        normalize(action.id())
                );
            }
        }

        if (action == null) {
            return null;
        }

        Action finalAction = action;

        actions.entrySet()
                .removeIf(
                        entry ->
                                entry.getValue()
                                        == finalAction
                );

        return action;
    }

    public Optional<Action> find(String id) {
        if (id == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                actions.get(
                        normalize(id)
                )
        );
    }

    public Collection<Action> actions() {
        return List.copyOf(
                new LinkedHashSet<>(
                        primary.values()
                )
        );
    }

    private String normalize(String input) {
        return input
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
