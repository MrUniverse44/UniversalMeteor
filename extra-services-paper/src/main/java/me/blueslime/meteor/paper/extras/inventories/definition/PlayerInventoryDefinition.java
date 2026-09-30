package me.blueslime.meteor.paper.extras.inventories.definition;

import me.blueslime.meteor.paper.extras.interaction.InteractiveItemDefinition;

import java.util.*;

public final class PlayerInventoryDefinition {

    /**
     * Special locale used by inventories that are not localized.
     */
    public static final String GLOBAL_LOCALE = "*";

    private final String id;
    private final String locale;

    private final Map<String, InteractiveItemDefinition> items;

    private final List<InteractiveItemDefinition> orderedItems;

    public PlayerInventoryDefinition(
            String id,
            String locale,
            Map<String, InteractiveItemDefinition> items
    ) {
        if (
                id == null ||
                        id.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Inventory id cannot be empty"
            );
        }

        this.id =
                normalize(id);

        this.locale =
                normalizeLocale(
                        locale
                );

        LinkedHashMap<
                String,
                InteractiveItemDefinition
                > copy =
                new LinkedHashMap<>();

        if (items != null) {
            for (
                    Map.Entry<
                            String,
                            InteractiveItemDefinition
                            > entry :
                    items.entrySet()
            ) {
                if (
                        entry.getKey() == null ||
                                entry.getValue() == null
                ) {
                    continue;
                }

                String itemId =
                        normalize(
                                entry.getKey()
                        );

                InteractiveItemDefinition previous =
                        copy.put(
                                itemId,
                                entry.getValue()
                        );

                if (previous != null) {
                    throw new IllegalArgumentException(
                            "Duplicate item id '"
                                    + itemId
                                    + "' in inventory '"
                                    + id
                                    + "'"
                    );
                }
            }
        }

        this.items =
                Collections.unmodifiableMap(
                        copy
                );

        this.orderedItems =
                List.copyOf(
                        copy.values()
                );
    }

    public String id() {
        return id;
    }

    public String locale() {
        return locale;
    }

    public Map<
            String,
            InteractiveItemDefinition
            > items() {
        return items;
    }

    /**
     * Order is important for FirstEmptyPlacement.
     */
    public List<InteractiveItemDefinition> orderedItems() {
        return orderedItems;
    }

    public Optional<InteractiveItemDefinition> item(
            String itemId
    ) {
        if (itemId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                items.get(
                        normalize(
                                itemId
                        )
                )
        );
    }

    public boolean containsItem(
            String itemId
    ) {
        return item(itemId)
                .isPresent();
    }

    public boolean localized() {
        return !GLOBAL_LOCALE.equals(
                locale
        );
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int size() {
        return items.size();
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

    private static String normalizeLocale(
            String value
    ) {
        if (
                value == null ||
                        value.isBlank()
        ) {
            return GLOBAL_LOCALE;
        }

        if (
                GLOBAL_LOCALE.equals(
                        value.strip()
                )
        ) {
            return GLOBAL_LOCALE;
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