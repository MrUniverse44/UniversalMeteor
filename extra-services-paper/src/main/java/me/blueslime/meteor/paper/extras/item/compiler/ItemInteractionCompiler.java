package me.blueslime.meteor.paper.extras.item.compiler;

import me.blueslime.meteor.paper.extras.actions.runtime.ActionPlan;

import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionMode;
import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionPlan;

import me.blueslime.meteor.paper.extras.interaction.ConditionalActionPlan;
import me.blueslime.meteor.paper.extras.interaction.InteractionDefinition;
import me.blueslime.meteor.paper.extras.interaction.InteractionType;

import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ItemInteractionCompiler {

    private final ItemCompileContext context;

    public ItemInteractionCompiler(
            ItemCompileContext context
    ) {
        this.context =
                Objects.requireNonNull(
                        context,
                        "context"
                );
    }

    public Map<
            InteractionType,
            InteractionDefinition
            > compile(
            ConfigurationHandle configuration,
            String itemPath
    ) {
        Objects.requireNonNull(
                configuration,
                "configuration"
        );

        EnumMap<
                InteractionType,
                InteractionDefinition
                > interactions =
                new EnumMap<>(
                        InteractionType.class
                );

        /*
         * First compile legacy root actions.
         *
         * New interactions.any will override this
         * later if explicitly configured.
         */
        InteractionDefinition legacy =
                compileLegacy(
                        configuration,
                        itemPath
                );

        if (legacy != null) {
            interactions.put(
                    InteractionType.ANY,
                    legacy
            );
        }

        String interactionsPath =
                itemPath
                        + ".interactions";

        if (
                !configuration.contains(
                        interactionsPath
                )
        ) {
            return Map.copyOf(
                    interactions
            );
        }

        for (
                String interactionId :
                configuration.getKeys(
                        interactionsPath,
                        false
                )
        ) {
            String path =
                    interactionsPath
                            + "."
                            + interactionId;

            InteractionType type =
                    InteractionTypeResolver.resolve(
                            interactionId,
                            path
                    );

            InteractionDefinition definition =
                    compileInteraction(
                            configuration,
                            path,
                            type
                    );

            /*
             * Explicit modern configuration wins.
             */
            interactions.put(
                    type,
                    definition
            );
        }

        return Map.copyOf(
                interactions
        );
    }

    private InteractionDefinition compileLegacy(
            ConfigurationHandle configuration,
            String path
    ) {
        boolean hasActions =
                configuration.contains(
                        path + ".actions"
                );

        boolean hasDenied =
                configuration.contains(
                        path + ".denied-actions"
                );

        if (
                !hasActions &&
                        !hasDenied
        ) {
            return null;
        }

        ActionPlan actions =
                compileActions(
                        configuration,
                        path + ".actions"
                );

        ActionPlan denied =
                compileActions(
                        configuration,
                        path + ".denied-actions"
                );

        /*
         * Legacy root "conditions" are visibility
         * conditions and intentionally NOT used here.
         */
        ConditionalActionPlan execution =
                new ConditionalActionPlan(
                        ConditionPlan.EMPTY,
                        actions,
                        denied
                );

        return new InteractionDefinition(
                InteractionType.ANY,
                execution
        );
    }

    private InteractionDefinition compileInteraction(
            ConfigurationHandle configuration,
            String path,
            InteractionType type
    ) {
        ConditionMode mode =
                readConditionMode(
                        configuration,
                        path
                );

        ConditionPlan conditions =
                compileConditions(
                        configuration,
                        path + ".conditions",
                        mode
                );

        ActionPlan actions =
                compileActions(
                        configuration,
                        path + ".actions"
                );

        ActionPlan deniedActions =
                compileDeniedActions(
                        configuration,
                        path
                );

        return new InteractionDefinition(
                type,
                new ConditionalActionPlan(
                        conditions,
                        actions,
                        deniedActions
                )
        );
    }

    private ConditionPlan compileConditions(
            ConfigurationHandle configuration,
            String path,
            ConditionMode mode
    ) {
        if (
                !configuration.contains(
                        path
                )
        ) {
            return ConditionPlan.EMPTY;
        }

        List<String> source =
                configuration.getStringList(
                        path
                );

        if (source.isEmpty()) {
            return ConditionPlan.EMPTY;
        }

        try {
            return context.compileConditions(
                    source,
                    mode
            );

        } catch (Throwable throwable) {
            throw new ItemCompileException(
                    path,
                    "Unable to compile interaction conditions",
                    throwable
            );
        }
    }

    private ActionPlan compileActions(
            ConfigurationHandle configuration,
            String path
    ) {
        if (
                !configuration.contains(
                        path
                )
        ) {
            return ActionPlan.EMPTY;
        }

        List<String> source =
                configuration.getStringList(
                        path
                );

        if (source.isEmpty()) {
            return ActionPlan.EMPTY;
        }

        try {
            return context.compileActions(
                    source
            );

        } catch (Throwable throwable) {
            throw new ItemCompileException(
                    path,
                    "Unable to compile item actions",
                    throwable
            );
        }
    }

    private ActionPlan compileDeniedActions(
            ConfigurationHandle configuration,
            String path
    ) {
        String deniedPath =
                path + ".denied-actions";

        if (
                configuration.contains(
                        deniedPath
                )
        ) {
            return compileActions(
                    configuration,
                    deniedPath
            );
        }

        /*
         * Friendly alias.
         */
        String fallbackPath =
                path + ".on-denied";

        return compileActions(
                configuration,
                fallbackPath
        );
    }

    private ConditionMode readConditionMode(
            ConfigurationHandle configuration,
            String path
    ) {
        String raw =
                configuration.getString(
                        path + ".condition-mode",
                        configuration.getString(
                                path + ".conditions-mode",
                                "all"
                        )
                );

        return parseMode(
                raw,
                path
        );
    }

    private ConditionMode parseMode(
            String input,
            String path
    ) {
        if (
                input == null ||
                        input.isBlank()
        ) {
            return ConditionMode.ALL;
        }

        return switch (
                input
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
                            path,
                            "Unknown condition mode '"
                                    + input
                                    + "'"
                    );
        };
    }
}