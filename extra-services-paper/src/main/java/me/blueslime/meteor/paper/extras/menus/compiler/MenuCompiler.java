package me.blueslime.meteor.paper.extras.menus.compiler;

import me.blueslime.meteor.paper.extras.actions.ActionService;
import me.blueslime.meteor.paper.extras.actions.runtime.ActionPlan;

import me.blueslime.meteor.paper.extras.conditions.ConditionService;
import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionMode;
import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionPlan;

import me.blueslime.meteor.paper.extras.interaction.InteractiveItemDefinition;

import me.blueslime.meteor.paper.extras.interaction.placement.ArmorPlacement;
import me.blueslime.meteor.paper.extras.interaction.placement.FirstEmptyPlacement;
import me.blueslime.meteor.paper.extras.interaction.placement.OffhandPlacement;
import me.blueslime.meteor.paper.extras.interaction.placement.SlotPlacement;

import me.blueslime.meteor.paper.extras.item.compiler.ItemCompiler;

import me.blueslime.meteor.paper.extras.menus.definition.MenuDefinition;
import me.blueslime.meteor.paper.extras.menus.definition.MenuPolicy;

import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;

import java.util.*;

public final class MenuCompiler {

    private final ItemCompiler items;

    private final ActionService actions;

    private final ConditionService conditions;

    public MenuCompiler(
            ItemCompiler items,
            ActionService actions,
            ConditionService conditions
    ) {
        this.items =
                Objects.requireNonNull(
                        items
                );

        this.actions =
                Objects.requireNonNull(
                        actions
                );

        this.conditions =
                Objects.requireNonNull(
                        conditions
                );
    }

    public MenuDefinition compile(
            String menuId,
            ConfigurationHandle configuration
    ) {
        Objects.requireNonNull(
                configuration,
                "configuration"
        );

        try {
            int size =
                    compileSize(
                            configuration,
                            menuId
                    );

            String title =
                    configuration.getString(
                            "menu-settings.title",
                            configuration.getString(
                                    "menu-settings.name",
                                    menuId
                            )
                    );

            Map<
                    String,
                    InteractiveItemDefinition
                    > itemDefinitions =
                    items.compileChildren(
                            configuration,
                            "items"
                    );

            validatePlacements(
                    menuId,
                    size,
                    itemDefinitions
            );

            ConditionPlan openConditions =
                    compileConditions(
                            configuration,
                            "menu-settings.open.conditions",
                            readMode(
                                    configuration,
                                    "menu-settings.open"
                            )
                    );

            ActionPlan openActions =
                    compileActions(
                            configuration,
                            "menu-settings.open.actions",
                            "menu-settings.on-open"
                    );

            ActionPlan deniedOpenActions =
                    compileActions(
                            configuration,
                            "menu-settings.open.denied-actions",
                            "menu-settings.on-denied-open"
                    );

            ActionPlan closeActions =
                    compileActions(
                            configuration,
                            "menu-settings.close.actions",
                            "menu-settings.on-close"
                    );

            MenuPolicy policy =
                    compilePolicy(
                            configuration
                    );

            return new MenuDefinition(
                    menuId,
                    size,
                    title,
                    itemDefinitions,
                    openConditions,
                    openActions,
                    deniedOpenActions,
                    closeActions,
                    policy
            );

        } catch (
                MenuCompileException exception
        ) {
            throw exception;

        } catch (RuntimeException exception) {
            throw new MenuCompileException(
                    menuId,
                    "Unable to compile menu",
                    exception
            );
        }
    }

    private int compileSize(
            ConfigurationHandle configuration,
            String menuId
    ) {
        int size;

        if (
                configuration.contains(
                        "menu-settings.size"
                )
        ) {
            size =
                    configuration.getInt(
                            "menu-settings.size",
                            54
                    );

        } else {
            /*
             * Legacy key:
             *
             * menu-settings.rows: 54
             *
             * But if somebody writes rows: 6,
             * accepting actual row count is useful.
             */
            int legacy =
                    configuration.getInt(
                            "menu-settings.rows",
                            54
                    );

            size =
                    legacy >= 1 &&
                            legacy <= 6
                            ? legacy * 9
                            : legacy;
        }

        if (
                size < 9 ||
                        size > 54 ||
                        size % 9 != 0
        ) {
            throw new MenuCompileException(
                    menuId,
                    "Menu size must be a multiple of 9 between 9 and 54, got "
                            + size
            );
        }

        return size;
    }

    private MenuPolicy compilePolicy(
            ConfigurationHandle configuration
    ) {
        boolean overwriteLegacy =
                configuration.getBoolean(
                        "menu-settings.overwrite-listeners",
                        false
                );

        boolean legacyCancel =
                !overwriteLegacy;

        boolean cancelTop =
                configuration.getBoolean(
                        "menu-settings.policy.cancel-top-clicks",
                        legacyCancel
                );

        boolean cancelPlayer =
                configuration.getBoolean(
                        "menu-settings.policy.cancel-player-inventory-clicks",
                        legacyCancel
                );

        boolean cancelDrag =
                configuration.getBoolean(
                        "menu-settings.policy.cancel-drag",
                        legacyCancel
                );

        return new MenuPolicy(
                cancelTop,
                cancelPlayer,
                cancelDrag
        );
    }

    private void validatePlacements(
            String menuId,
            int size,
            Map<
                    String,
                    InteractiveItemDefinition
                    > definitions
    ) {
        Map<Integer, String> occupied =
                new HashMap<>();

        for (
                InteractiveItemDefinition item :
                definitions.values()
        ) {
            switch (
                    item.placement()
            ) {
                case SlotPlacement slots -> {
                    for (
                            int slot :
                            slots.slots()
                    ) {
                        if (
                                slot < 0 ||
                                        slot >= size
                        ) {
                            throw new MenuCompileException(
                                    menuId,
                                    "Item '"
                                            + item.id()
                                            + "' uses invalid menu slot "
                                            + slot
                                            + " for size "
                                            + size
                            );
                        }

                        String previous =
                                occupied.putIfAbsent(
                                        slot,
                                        item.id()
                                );

                        if (previous != null) {
                            throw new MenuCompileException(
                                    menuId,
                                    "Items '"
                                            + previous
                                            + "' and '"
                                            + item.id()
                                            + "' both use slot "
                                            + slot
                            );
                        }
                    }
                }

                case FirstEmptyPlacement ignored -> {
                    /*
                     * Valid.
                     */
                }

                case ArmorPlacement ignored ->
                        throw new MenuCompileException(
                                menuId,
                                "ArmorPlacement cannot be used inside a menu"
                        );

                case OffhandPlacement ignored ->
                        throw new MenuCompileException(
                                menuId,
                                "OffhandPlacement cannot be used inside a menu"
                        );
            }
        }
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

        return conditions.compile(
                source,
                mode
        );
    }

    private ActionPlan compileActions(
            ConfigurationHandle configuration,
            String primary,
            String fallback
    ) {
        if (
                configuration.contains(
                        primary
                )
        ) {
            List<String> values =
                    configuration.getStringList(
                            primary
                    );

            if (!values.isEmpty()) {
                return actions.compile(
                        values
                );
            }
        }

        if (
                configuration.contains(
                        fallback
                )
        ) {
            List<String> values =
                    configuration.getStringList(
                            fallback
                    );

            if (!values.isEmpty()) {
                return actions.compile(
                        values
                );
            }
        }

        return ActionPlan.EMPTY;
    }

    private ConditionMode readMode(
            ConfigurationHandle configuration,
            String path
    ) {
        String value =
                configuration.getString(
                        path + ".condition-mode",
                        "all"
                );

        return switch (
                value
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
                    throw new IllegalArgumentException(
                            "Unknown condition mode '"
                                    + value
                                    + "'"
                    );
        };
    }
}