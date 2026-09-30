package me.blueslime.meteor.paper.extras.inventories.registry;

import me.blueslime.meteor.paper.extras.inventories.definition.PlayerInventoryDefinition;

import java.util.*;

public final class InventoryRegistry {

    private volatile Map<
            String,
            LocalizedDefinition<
                    PlayerInventoryDefinition
                    >
            > inventories =
            Map.of();

    private volatile String fallbackLocale =
            "en";

    public synchronized void replaceAll(
            Collection<PlayerInventoryDefinition> definitions,
            String fallbackLocale
    ) {
        Map<
                String,
                LinkedHashMap<
                        String,
                        PlayerInventoryDefinition
                        >
                > grouped =
                new LinkedHashMap<>();

        for (
                PlayerInventoryDefinition definition :
                definitions
        ) {
            grouped
                    .computeIfAbsent(
                            normalize(
                                    definition.id()
                            ),
                            ignored ->
                                    new LinkedHashMap<>()
                    )
                    .put(
                            normalizeLocale(
                                    definition.locale()
                            ),
                            definition
                    );
        }

        LinkedHashMap<
                String,
                LocalizedDefinition<
                        PlayerInventoryDefinition
                        >
                > compiled =
                new LinkedHashMap<>();

        grouped.forEach(
                (id, localized) ->
                        compiled.put(
                                id,
                                new LocalizedDefinition<>(
                                        localized
                                )
                        )
        );

        this.fallbackLocale =
                normalizeLocale(
                        fallbackLocale
                );

        /*
         * Atomic publication.
         */
        this.inventories =
                Collections.unmodifiableMap(
                        compiled
                );
    }

    public Optional<PlayerInventoryDefinition> find(
            String inventoryId,
            String locale
    ) {
        if (inventoryId == null) {
            return Optional.empty();
        }

        LocalizedDefinition<
                PlayerInventoryDefinition
                > localized =
                inventories.get(
                        normalize(
                                inventoryId
                        )
                );

        if (localized == null) {
            return Optional.empty();
        }

        return localized.resolve(
                locale,
                fallbackLocale
        );
    }

    public Optional<
            LocalizedDefinition<
                    PlayerInventoryDefinition
                    >
            > find(
            String inventoryId
    ) {
        if (inventoryId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                inventories.get(
                        normalize(
                                inventoryId
                        )
                )
        );
    }

    public Collection<String> ids() {
        return List.copyOf(
                inventories.keySet()
        );
    }

    public int size() {
        return inventories.size();
    }

    public boolean contains(
            String inventoryId
    ) {
        return inventoryId != null &&
                inventories.containsKey(
                        normalize(
                                inventoryId
                        )
                );
    }

    public String fallbackLocale() {
        return fallbackLocale;
    }

    public void clear() {
        inventories =
                Map.of();
    }

    private String normalize(
            String value
    ) {
        return value
                .strip()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    private String normalizeLocale(
            String value
    ) {
        if (
                value == null ||
                        value.isBlank()
        ) {
            return "en";
        }

        return value
                .strip()
                .toLowerCase(
                        Locale.ROOT
                )
                .replace(
                        '-',
                        '_'
                );
    }
}