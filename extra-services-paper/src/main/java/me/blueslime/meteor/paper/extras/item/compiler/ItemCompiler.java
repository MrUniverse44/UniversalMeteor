package me.blueslime.meteor.paper.extras.item.compiler;

import me.blueslime.meteor.paper.extras.actions.ActionService;

import me.blueslime.meteor.paper.extras.conditions.ConditionService;
import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionMode;
import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionPlan;

import me.blueslime.meteor.paper.extras.interaction.InteractionDefinition;
import me.blueslime.meteor.paper.extras.interaction.InteractionPolicy;
import me.blueslime.meteor.paper.extras.interaction.InteractionType;
import me.blueslime.meteor.paper.extras.interaction.InteractiveItemDefinition;

import me.blueslime.meteor.paper.extras.interaction.placement.ItemPlacement;

import me.blueslime.meteor.paper.extras.item.ItemWrapper;

import me.blueslime.meteor.paper.extras.item.definition.ItemDefinition;
import me.blueslime.meteor.paper.extras.item.definition.ItemRenderPolicy;

import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ItemCompiler {

    private final ItemCompileContext context;

    private final ItemPlacementCompiler placements;

    private final ItemInteractionCompiler interactions;

    private final ItemAnimationCompiler animations;

    public ItemCompiler(
            ActionService actions,
            ConditionService conditions
    ) {
        this(
                new ItemCompileContext(
                        actions,
                        conditions
                )
        );
    }

    public ItemCompiler(
            ItemCompileContext context
    ) {
        this.context =
                Objects.requireNonNull(
                        context,
                        "context"
                );

        this.placements =
                new ItemPlacementCompiler();

        this.interactions =
                new ItemInteractionCompiler(
                        context
                );

        this.animations =
                new ItemAnimationCompiler(
                        placements
                );
    }

    /**
     * Uses the final segment of path as item id.
     *
     * Example:
     *
     * items.selector.survival
     *
     * becomes:
     *
     * survival
     */
    public InteractiveItemDefinition compile(
            ConfigurationHandle configuration,
            String path
    ) {
        return compile(
                configuration,
                path,
                extractId(path)
        );
    }

    /**
     * Main compilation entry point.
     */
    public InteractiveItemDefinition compile(
            ConfigurationHandle configuration,
            String path,
            String itemId
    ) {
        Objects.requireNonNull(
                configuration,
                "configuration"
        );

        if (
                path == null ||
                        path.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Item path cannot be empty"
            );
        }

        if (
                itemId == null ||
                        itemId.isBlank()
        ) {
            throw new ItemCompileException(
                    path,
                    "Item id cannot be empty"
            );
        }

        try {
            /*
             * ItemWrapper reads ONLY visual/item
             * metadata now.
             */
            ItemWrapper wrapper =
                    ItemWrapper.fromData(
                            configuration,
                            path
                    );

            ItemRenderPolicy renderPolicy =
                    compileRenderPolicy(
                            configuration,
                            path
                    );

            ItemDefinition item =
                    ItemDefinition.from(
                            wrapper,
                            renderPolicy
                    );

            /*
             * Legacy root conditions and modern
             * visibility.conditions both become
             * visibility.
             */
            ConditionPlan visibility =
                    compileVisibility(
                            configuration,
                            path
                    );

            ItemPlacement placement =
                    placements.compile(
                            configuration,
                            path,
                            wrapper
                    );

            InteractionPolicy policy =
                    compileInteractionPolicy(
                            configuration,
                            path
                    );

            Map<
                    InteractionType,
                    InteractionDefinition
                    > compiledInteractions =
                    interactions.compile(
                            configuration,
                            path
                    );

            AnimationDefinition animation =
                    animations.compile(
                            configuration,
                            path,
                            wrapper,
                            renderPolicy
                    );

            return new InteractiveItemDefinition(
                    itemId,
                    item,
                    visibility,
                    placement,
                    compiledInteractions,
                    policy,
                    animation
            );

        } catch (
                ItemCompileException exception
        ) {
            throw exception;

        } catch (Throwable throwable) {
            throw new ItemCompileException(
                    path,
                    "Unable to compile item '"
                            + itemId
                            + "'",
                    throwable
            );
        }
    }

    /**
     * Useful for:
     *
     * menus:
     *   items:
     *     one:
     *     two:
     *
     * or inventory definitions.
     */
    public Map<
            String,
            InteractiveItemDefinition
            > compileChildren(
            ConfigurationHandle configuration,
            String rootPath
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

        Map<
                String,
                InteractiveItemDefinition
                > result =
                new LinkedHashMap<>();

        if (
                !configuration.contains(
                        rootPath
                )
        ) {
            return Map.of();
        }

        for (
                String itemId :
                configuration.getKeys(
                        rootPath,
                        false
                )
        ) {
            String path =
                    rootPath
                            + "."
                            + itemId;

            InteractiveItemDefinition definition =
                    compile(
                            configuration,
                            path,
                            itemId
                    );

            InteractiveItemDefinition previous =
                    result.put(
                            itemId,
                            definition
                    );

            if (previous != null) {
                throw new ItemCompileException(
                        path,
                        "Duplicate item id '"
                                + itemId
                                + "'"
                );
            }
        }

        return Map.copyOf(
                result
        );
    }

    private ConditionPlan compileVisibility(
            ConfigurationHandle configuration,
            String path
    ) {
        String modernPath =
                path
                        + ".visibility.conditions";

        /*
         * Modern configuration has priority.
         */
        if (
                configuration.contains(
                        modernPath
                )
        ) {
            ConditionMode mode =
                    readConditionMode(
                            configuration,
                            path
                                    + ".visibility",
                            "mode"
                    );

            return compileConditions(
                    configuration,
                    modernPath,
                    mode
            );
        }

        /*
         * Legacy:
         *
         * conditions:
         *   - "[permission] ..."
         *
         * Historically this decided whether
         * getUserItem() returned null.
         *
         * Therefore it maps to visibility.
         */
        String legacyPath =
                path
                        + ".conditions";

        if (
                configuration.contains(
                        legacyPath
                )
        ) {
            ConditionMode mode =
                    readConditionMode(
                            configuration,
                            path,
                            "conditions-mode"
                    );

            return compileConditions(
                    configuration,
                    legacyPath,
                    mode
            );
        }

        return ConditionPlan.EMPTY;
    }

    private ConditionPlan compileConditions(
            ConfigurationHandle configuration,
            String path,
            ConditionMode mode
    ) {
        List<String> raw =
                configuration.getStringList(
                        path
                );

        if (raw.isEmpty()) {
            return ConditionPlan.EMPTY;
        }

        try {
            return context.compileConditions(
                    raw,
                    mode
            );

        } catch (Throwable throwable) {
            throw new ItemCompileException(
                    path,
                    "Unable to compile visibility conditions",
                    throwable
            );
        }
    }

    private ItemRenderPolicy compileRenderPolicy(
            ConfigurationHandle configuration,
            String path
    ) {
        String raw =
                configuration.getString(
                        path + ".render-policy",
                        "auto"
                );

        if (
                raw == null ||
                        raw.isBlank()
        ) {
            return ItemRenderPolicy.AUTO;
        }

        return switch (
                raw
                        .strip()
                        .toLowerCase(
                                Locale.ROOT
                        )
                ) {
            case "auto",
                 "automatic" ->
                    ItemRenderPolicy.AUTO;

            case "static",
                 "fixed" ->
                    ItemRenderPolicy.STATIC;

            case "dynamic",
                 "runtime" ->
                    ItemRenderPolicy.DYNAMIC;

            default ->
                    throw new ItemCompileException(
                            path + ".render-policy",
                            "Unknown render policy '"
                                    + raw
                                    + "'"
                    );
        };
    }

    private InteractionPolicy compileInteractionPolicy(
            ConfigurationHandle configuration,
            String path
    ) {
        String policyPath =
                path + ".policy";

        /*
         * Modern keys have priority, with legacy
         * allow-item-* keys as fallback.
         */

        boolean allowMove =
                readBoolean(
                        configuration,
                        policyPath + ".allow-move",
                        path + ".allow-item-move",
                        false
                );

        boolean allowDrop =
                readBoolean(
                        configuration,
                        policyPath + ".allow-drop",
                        path + ".allow-item-drop",
                        false
                );

        boolean allowDrag =
                readBoolean(
                        configuration,
                        policyPath + ".allow-drag",
                        path + ".allow-item-drag",
                        false
                );

        boolean allowWorldInteraction =
                configuration.getBoolean(
                        policyPath
                                + ".allow-world-interaction",
                        false
                );

        boolean cancelUnhandled =
                configuration.getBoolean(
                        policyPath
                                + ".cancel-unhandled-interactions",
                        true
                );

        return new InteractionPolicy(
                allowMove,
                allowDrop,
                allowDrag,
                allowWorldInteraction,
                cancelUnhandled
        );
    }

    private boolean readBoolean(
            ConfigurationHandle configuration,
            String modernPath,
            String legacyPath,
            boolean fallback
    ) {
        if (
                configuration.contains(
                        modernPath
                )
        ) {
            return configuration.getBoolean(
                    modernPath,
                    fallback
            );
        }

        if (
                configuration.contains(
                        legacyPath
                )
        ) {
            return configuration.getBoolean(
                    legacyPath,
                    fallback
            );
        }

        return fallback;
    }

    private ConditionMode readConditionMode(
            ConfigurationHandle configuration,
            String path,
            String key
    ) {
        String raw =
                configuration.getString(
                        path + "." + key,
                        "all"
                );

        if (
                raw == null ||
                        raw.isBlank()
        ) {
            return ConditionMode.ALL;
        }

        return switch (
                raw
                        .strip()
                        .toLowerCase(
                                Locale.ROOT
                        )
                ) {
            case "all",
                 "and" ->
                    ConditionMode.ALL;

            case "any",
                 "or",
                 "one" ->
                    ConditionMode.ANY;

            default ->
                    throw new ItemCompileException(
                            path + "." + key,
                            "Unknown condition mode '"
                                    + raw
                                    + "'"
                    );
        };
    }

    private String extractId(
            String path
    ) {
        if (
                path == null ||
                        path.isBlank()
        ) {
            return "item";
        }

        int last =
                path.lastIndexOf(
                        '.'
                );

        if (
                last < 0 ||
                        last ==
                                path.length() - 1
        ) {
            return path;
        }

        return path.substring(
                last + 1
        );
    }

    public ItemCompileContext context() {
        return context;
    }
}