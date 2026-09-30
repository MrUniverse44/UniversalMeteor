package me.blueslime.meteor.paper.extras.menus.registry;

import me.blueslime.meteor.paper.extras.menus.definition.MenuDefinition;

import java.util.*;

public final class MenuRegistry {

    private volatile Map<
            String,
            MenuDefinition
            > menus =
            Map.of();

    public synchronized void replaceAll(
            Collection<MenuDefinition> definitions
    ) {
        LinkedHashMap<
                String,
                MenuDefinition
                > compiled =
                new LinkedHashMap<>();

        for (
                MenuDefinition definition :
                definitions
        ) {
            String id =
                    normalize(
                            definition.id()
                    );

            MenuDefinition previous =
                    compiled.put(
                            id,
                            definition
                    );

            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate menu id '"
                                + id
                                + "'"
                );
            }
        }

        this.menus =
                Collections.unmodifiableMap(
                        compiled
                );
    }

    public Optional<MenuDefinition> find(
            String id
    ) {
        if (id == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                menus.get(
                        normalize(id)
                )
        );
    }

    public boolean contains(
            String id
    ) {
        return find(id)
                .isPresent();
    }

    public Collection<MenuDefinition> definitions() {
        return List.copyOf(
                menus.values()
        );
    }

    public Set<String> ids() {
        return Set.copyOf(
                menus.keySet()
        );
    }

    public int size() {
        return menus.size();
    }

    public void clear() {
        menus =
                Map.of();
    }

    private String normalize(
            String input
    ) {
        return input
                .strip()
                .toLowerCase(
                        Locale.ROOT
                )
                .replace(
                        ".yml",
                        ""
                );
    }
}