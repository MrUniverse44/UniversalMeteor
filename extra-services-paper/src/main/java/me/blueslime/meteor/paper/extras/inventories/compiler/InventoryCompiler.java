package me.blueslime.meteor.paper.extras.inventories.compiler;

import me.blueslime.meteor.paper.extras.interaction.InteractiveItemDefinition;

import me.blueslime.meteor.paper.extras.inventories.definition.PlayerInventoryDefinition;

import me.blueslime.meteor.paper.extras.item.compiler.ItemCompiler;

import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;

import java.util.*;

public final class InventoryCompiler {

    private final ItemCompiler items;

    public InventoryCompiler(
            ItemCompiler items
    ) {
        this.items =
                Objects.requireNonNull(
                        items,
                        "items"
                );
    }

    public List<PlayerInventoryDefinition> compile(
            ConfigurationHandle configuration,
            String rootPath,
            InventoryLayout layout
    ) {
        Objects.requireNonNull(
                configuration,
                "configuration"
        );

        if (
                rootPath == null ||
                        rootPath.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "rootPath cannot be empty"
            );
        }

        if (
                !configuration.contains(
                        rootPath
                )
        ) {
            return List.of();
        }

        return switch (layout) {
            case STATIC ->
                    compileStatic(
                            configuration,
                            rootPath
                    );

            case LOCALIZED ->
                    compileLocalized(
                            configuration,
                            rootPath
                    );

            case AUTO ->
                    throw new IllegalArgumentException(
                            "InventoryCompiler requires STATIC or LOCALIZED. "
                                    + "AUTO must be resolved by InventoryService first."
                    );
        };
    }

    private List<PlayerInventoryDefinition> compileStatic(
            ConfigurationHandle configuration,
            String rootPath
    ) {
        List<PlayerInventoryDefinition> result =
                new ArrayList<>();

        for (
                String inventoryId :
                configuration.getKeys(
                        rootPath,
                        false
                )
        ) {
            String inventoryPath =
                    rootPath
                            + "."
                            + inventoryId;

            Map<
                    String,
                    InteractiveItemDefinition
                    > definitions =
                    items.compileChildren(
                            configuration,
                            inventoryPath
                    );

            result.add(
                    new PlayerInventoryDefinition(
                            inventoryId,
                            PlayerInventoryDefinition.GLOBAL_LOCALE,
                            definitions
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    private List<PlayerInventoryDefinition> compileLocalized(
            ConfigurationHandle configuration,
            String rootPath
    ) {
        List<PlayerInventoryDefinition> result =
                new ArrayList<>();

        for (
                String locale :
                configuration.getKeys(
                        rootPath,
                        false
                )
        ) {
            String localePath =
                    rootPath
                            + "."
                            + locale;

            for (
                    String inventoryId :
                    configuration.getKeys(
                            localePath,
                            false
                    )
            ) {
                String inventoryPath =
                        localePath
                                + "."
                                + inventoryId;

                Map<
                        String,
                        InteractiveItemDefinition
                        > definitions =
                        items.compileChildren(
                                configuration,
                                inventoryPath
                        );

                result.add(
                        new PlayerInventoryDefinition(
                                inventoryId,
                                locale,
                                definitions
                        )
                );
            }
        }

        return List.copyOf(
                result
        );
    }
}