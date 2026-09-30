package me.blueslime.meteor.paper.extras.inventories.compiler;

import me.blueslime.meteor.paper.extras.interaction.InteractiveItemDefinition;

import me.blueslime.meteor.paper.extras.inventories.definition.PlayerInventoryDefinition;

import me.blueslime.meteor.paper.extras.item.compiler.ItemCompiler;

import me.blueslime.meteor.paper.extras.runtime.compiler.CompilationReporter;
import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class InventoryCompiler {

    private static final String ITEMS_SECTION =
            "items";

    private final ItemCompiler items;

    private final CompilationReporter reporter;

    public InventoryCompiler(
            ItemCompiler items
    ) {
        this(
            items,
            CompilationReporter.noop()
        );
    }

    public InventoryCompiler(
            ItemCompiler items,
            CompilationReporter reporter
    ) {
        this.items =
                Objects.requireNonNull(
                        items,
                        "items"
                );

        this.reporter =
                Objects.requireNonNull(
                        reporter,
                        "reporter"
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

        Objects.requireNonNull(
                layout,
                "layout"
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

            try {
                Map<
                        String,
                        InteractiveItemDefinition
                        > definitions =
                        compileInventoryItems(
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

            } catch (
                    RuntimeException exception
            ) {
                reporter.report(
                        "inventory",
                        inventoryId,
                        inventoryPath,
                        exception
                );
            }
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

                try {
                    Map<
                            String,
                            InteractiveItemDefinition
                            > definitions =
                            compileInventoryItems(
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

                } catch (
                        RuntimeException exception
                ) {
                    reporter.report(
                            "inventory",
                            inventoryId
                                    + "["
                                    + locale
                                    + "]",
                            inventoryPath,
                            exception
                    );
                }
            }
        }

        return List.copyOf(
                result
        );
    }

    private Map<
            String,
            InteractiveItemDefinition
            > compileInventoryItems(
            ConfigurationHandle configuration,
            String inventoryPath
    ) {
        String modernItemsPath =
                inventoryPath
                        + "."
                        + ITEMS_SECTION;

        if (
                configuration.contains(
                        modernItemsPath
                )
        ) {
            return items.compileChildren(
                    configuration,
                    modernItemsPath
            );
        }

        return items.compileChildren(
                configuration,
                inventoryPath
        );
    }
}