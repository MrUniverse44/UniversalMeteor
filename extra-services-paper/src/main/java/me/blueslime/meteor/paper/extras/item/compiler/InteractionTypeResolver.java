package me.blueslime.meteor.paper.extras.item.compiler;

import me.blueslime.meteor.paper.extras.interaction.InteractionType;

import java.util.Locale;

public final class InteractionTypeResolver {

    private InteractionTypeResolver() {}

    public static InteractionType resolve(
            String input,
            String path
    ) {
        if (
                input == null ||
                        input.isBlank()
        ) {
            throw new ItemCompileException(
                    path,
                    "Interaction type cannot be empty"
            );
        }

        String value =
                normalize(
                        input
                );

        return switch (value) {
            case "any",
                 "all",
                 "click",
                 "default" ->
                    InteractionType.ANY;

            case "left",
                 "left-click",
                 "leftclick" ->
                    InteractionType.LEFT_CLICK;

            case "right",
                 "right-click",
                 "rightclick" ->
                    InteractionType.RIGHT_CLICK;

            case "shift-left",
                 "shift-left-click",
                 "shiftleft",
                 "shiftleftclick" ->
                    InteractionType.SHIFT_LEFT_CLICK;

            case "shift-right",
                 "shift-right-click",
                 "shiftright",
                 "shiftrightclick" ->
                    InteractionType.SHIFT_RIGHT_CLICK;

            case "middle",
                 "middle-click",
                 "middleclick" ->
                    InteractionType.MIDDLE_CLICK;

            case "double",
                 "double-click",
                 "doubleclick" ->
                    InteractionType.DOUBLE_CLICK;

            case "number",
                 "number-key",
                 "numberkey",
                 "hotbar" ->
                    InteractionType.NUMBER_KEY;

            case "drop",
                 "q" ->
                    InteractionType.DROP;

            case "control-drop",
                 "ctrl-drop",
                 "controldrop",
                 "ctrldrop" ->
                    InteractionType.CONTROL_DROP;

            case "swap-offhand",
                 "swap-off-hand",
                 "swapoffhand",
                 "offhand-swap" ->
                    InteractionType.SWAP_OFFHAND;

            case "creative",
                 "creative-click" ->
                    InteractionType.CREATIVE;

            case "left-interact",
                 "left-world",
                 "left-use" ->
                    InteractionType.LEFT_INTERACT;

            case "right-interact",
                 "right-world",
                 "right-use",
                 "interact" ->
                    InteractionType.RIGHT_INTERACT;

            case "unknown" ->
                    InteractionType.UNKNOWN;

            default ->
                    throw new ItemCompileException(
                            path,
                            "Unknown interaction type '"
                                    + input
                                    + "'"
                    );
        };
    }

    private static String normalize(
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