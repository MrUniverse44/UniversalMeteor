package me.blueslime.meteor.paper.extras.item.compiler;

import me.blueslime.meteor.paper.extras.interaction.placement.ArmorPlacement;
import me.blueslime.meteor.paper.extras.interaction.placement.FirstEmptyPlacement;
import me.blueslime.meteor.paper.extras.interaction.placement.ItemPlacement;
import me.blueslime.meteor.paper.extras.interaction.placement.OffhandPlacement;
import me.blueslime.meteor.paper.extras.interaction.placement.SlotPlacement;

import me.blueslime.meteor.paper.extras.item.ItemWrapper;
import me.blueslime.meteor.paper.extras.item.armor.ItemArmorSlot;

import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class ItemPlacementCompiler {

    public ItemPlacement compile(
            ConfigurationHandle configuration,
            String path,
            ItemWrapper wrapper
    ) {
        Objects.requireNonNull(
                configuration,
                "configuration"
        );

        Objects.requireNonNull(
                wrapper,
                "wrapper"
        );

        String placementPath =
                path + ".placement";

        /*
         * New format has priority over legacy.
         */
        if (
                configuration.contains(
                        placementPath + ".type"
                )
        ) {
            return compileExplicit(
                    configuration,
                    placementPath,
                    wrapper
            );
        }

        return compileLegacy(
                configuration,
                path,
                wrapper
        );
    }

    private ItemPlacement compileExplicit(
            ConfigurationHandle configuration,
            String path,
            ItemWrapper wrapper
    ) {
        String rawType =
                configuration.getString(
                        path + ".type",
                        "first-empty"
                );

        String type =
                normalize(
                        rawType
                );

        return switch (type) {
            case "slot",
                 "slots" ->
                    compileSlotPlacement(
                            configuration,
                            path
                    );

            case "armor",
                 "armour",
                 "auto-equip",
                 "autoequip" ->
                    compileArmorPlacement(
                            configuration,
                            path,
                            wrapper
                    );

            case "offhand",
                 "off-hand" ->
                    OffhandPlacement.INSTANCE;

            case "first-empty",
                 "firstempty",
                 "empty",
                 "automatic",
                 "default" ->
                    FirstEmptyPlacement.INSTANCE;

            case "auto" ->
                    autoDetect(
                            configuration,
                            path,
                            wrapper
                    );

            default ->
                    throw new ItemCompileException(
                            path,
                            "Unknown placement type '"
                                    + rawType
                                    + "'"
                    );
        };
    }

    private ItemPlacement compileSlotPlacement(
            ConfigurationHandle configuration,
            String path
    ) {
        if (
                configuration.contains(
                        path + ".slots"
                )
        ) {
            List<Integer> slots =
                    configuration.getIntList(
                            path + ".slots"
                    );

            if (slots.isEmpty()) {
                throw new ItemCompileException(
                        path,
                        "placement.slots cannot be empty"
                );
            }

            return SlotPlacement.of(
                    slots
            );
        }

        if (
                configuration.contains(
                        path + ".slot"
                )
        ) {
            return SlotPlacement.of(
                    configuration.getInt(
                            path + ".slot",
                            0
                    )
            );
        }

        throw new ItemCompileException(
                path,
                "Slot placement requires 'slot' or 'slots'"
        );
    }

    private ItemPlacement compileArmorPlacement(
            ConfigurationHandle configuration,
            String path,
            ItemWrapper wrapper
    ) {
        String configuredSlot =
                null;

        if (
                configuration.contains(
                        path + ".armor-slot"
                )
        ) {
            configuredSlot =
                    configuration.getString(
                            path + ".armor-slot"
                    );

        } else if (
                configuration.contains(
                        path + ".slot"
                )
        ) {
            configuredSlot =
                    configuration.getString(
                            path + ".slot"
                    );
        }

        if (
                configuredSlot != null &&
                        !configuredSlot.isBlank()
        ) {
            ItemArmorSlot slot =
                    parseArmorSlot(
                            configuredSlot,
                            path
                    );

            return new ArmorPlacement(
                    slot
            );
        }

        Optional<ItemArmorSlot> detected =
                wrapper.getArmorSlot();

        if (detected.isPresent()) {
            return new ArmorPlacement(
                    detected.get()
            );
        }

        throw new ItemCompileException(
                path,
                "Armor placement was requested but material '"
                        + wrapper.getMaterial()
                        + "' has no detectable armor slot. "
                        + "Specify placement.armor-slot explicitly."
        );
    }

    private ItemPlacement autoDetect(
            ConfigurationHandle configuration,
            String path,
            ItemWrapper wrapper
    ) {
        Optional<ItemArmorSlot> armor =
                wrapper.getArmorSlot();

        if (armor.isPresent()) {
            return new ArmorPlacement(
                    armor.get()
            );
        }

        if (
                configuration.contains(
                        path + ".slots"
                ) ||
                        configuration.contains(
                                path + ".slot"
                        )
        ) {
            return compileSlotPlacement(
                    configuration,
                    path
            );
        }

        return FirstEmptyPlacement.INSTANCE;
    }

    /**
     * Compatibility with old ItemWrapper behavior.
     */
    private ItemPlacement compileLegacy(
            ConfigurationHandle configuration,
            String path,
            ItemWrapper wrapper
    ) {
        boolean autoEquip =
                configuration.getBoolean(
                        path + ".auto-equip",
                        false
                );

        if (autoEquip) {
            Optional<ItemArmorSlot> armor =
                    wrapper.getArmorSlot();

            /*
             * Old behavior:
             *
             * auto-equip=true on a non-armor item
             * did NOT prevent it from going through
             * normal slot placement.
             *
             * Preserve that behavior.
             */
            if (armor.isPresent()) {
                return new ArmorPlacement(
                        armor.get()
                );
            }
        }

        if (
                configuration.contains(
                        path + ".slots"
                )
        ) {
            List<Integer> slots =
                    configuration.getIntList(
                            path + ".slots"
                    );

            if (!slots.isEmpty()) {
                return SlotPlacement.of(
                        slots
                );
            }
        }

        if (
                configuration.contains(
                        path + ".slot"
                )
        ) {
            return SlotPlacement.of(
                    configuration.getInt(
                            path + ".slot",
                            0
                    )
            );
        }

        return FirstEmptyPlacement.INSTANCE;
    }

    private ItemArmorSlot parseArmorSlot(
            String value,
            String path
    ) {
        String normalized =
                value
                        .strip()
                        .toUpperCase(
                                Locale.ROOT
                        )
                        .replace(
                                '-',
                                '_'
                        )
                        .replace(
                                ' ',
                                '_'
                        );

        try {
            return ItemArmorSlot.valueOf(
                    normalized
            );

        } catch (
                IllegalArgumentException exception
        ) {
            return ItemArmorSlot
                    .anyMatch(
                            normalized
                    )
                    .orElseThrow(
                            () ->
                                    new ItemCompileException(
                                            path,
                                            "Unknown armor slot '"
                                                    + value
                                                    + "'"
                                    )
                    );
        }
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
                        '_',
                        '-'
                )
                .replace(
                        ' ',
                        '-'
                );
    }
}