package me.blueslime.meteor.paper.extras.item.compiler;

import me.blueslime.meteor.paper.extras.animation.AnimationDefinition;
import me.blueslime.meteor.paper.extras.animation.AnimationFrame;
import me.blueslime.meteor.paper.extras.animation.AnimationMode;

import me.blueslime.meteor.paper.extras.interaction.placement.ItemPlacement;

import me.blueslime.meteor.paper.extras.item.ItemWrapper;

import me.blueslime.meteor.paper.extras.item.definition.ItemDefinition;
import me.blueslime.meteor.paper.extras.item.definition.ItemRenderPolicy;

import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ItemAnimationCompiler {

    private final ItemPlacementCompiler placements;

    public ItemAnimationCompiler(
            ItemPlacementCompiler placements
    ) {
        this.placements =
                Objects.requireNonNull(
                        placements
                );
    }

    public AnimationDefinition compile(
            ConfigurationHandle configuration,
            String itemPath,
            ItemWrapper baseItem,
            ItemRenderPolicy baseRenderPolicy
    ) {
        String path =
                itemPath + ".animation";

        if (
                !configuration.contains(path) ||
                        !configuration.getBoolean(
                                path + ".enabled",
                                true
                        )
        ) {
            return AnimationDefinition.NONE;
        }

        String framesPath =
                path + ".frames";

        if (
                !configuration.contains(
                        framesPath
                )
        ) {
            throw new ItemCompileException(
                    path,
                    "Animation requires a frames section"
            );
        }

        AnimationMode mode;

        try {
            mode =
                    AnimationMode.parse(
                            configuration.getString(
                                    path + ".mode",
                                    "loop"
                            )
                    );

        } catch (
                IllegalArgumentException exception
        ) {
            throw new ItemCompileException(
                    path + ".mode",
                    exception.getMessage(),
                    exception
            );
        }

        long initialDelay =
                parseTicks(
                        configuration.getString(
                                path + ".initial-delay",
                                "0t"
                        ),
                        true,
                        path + ".initial-delay"
                );

        List<String> frameKeys =
                new ArrayList<>(
                        configuration.getKeys(
                                framesPath,
                                false
                        )
                );

        frameKeys.sort(
                Comparator.comparingInt(
                        this::numericOrder
                ).thenComparing(
                        value -> value
                )
        );

        List<AnimationFrame> frames =
                new ArrayList<>();

        for (String frameKey : frameKeys) {
            String framePath =
                    framesPath
                            + "."
                            + frameKey;

            ItemWrapper frameItem =
                    baseItem.copy();

            /*
             * Both formats work:
             *
             * frames.0.item.name
             *
             * or simply
             *
             * frames.0.name
             */
            if (
                    configuration.contains(
                            framePath + ".item"
                    )
            ) {
                frameItem.applyData(
                        configuration,
                        framePath + ".item"
                );

            } else {
                frameItem.applyData(
                        configuration,
                        framePath
                );
            }

            ItemRenderPolicy renderPolicy =
                    compileRenderPolicy(
                            configuration,
                            framePath,
                            baseRenderPolicy
                    );

            ItemDefinition definition =
                    ItemDefinition.from(
                            frameItem,
                            renderPolicy
                    );

            ItemPlacement placement =
                    hasPlacementOverride(
                            configuration,
                            framePath
                    )
                            ? placements.compile(
                            configuration,
                            framePath,
                            frameItem
                    )
                            : null;

            long duration =
                    parseTicks(
                            configuration.getString(
                                    framePath + ".duration",
                                    "10t"
                            ),
                            false,
                            framePath + ".duration"
                    );

            frames.add(
                    new AnimationFrame(
                            definition,
                            placement,
                            duration
                    )
            );
        }

        if (frames.isEmpty()) {
            throw new ItemCompileException(
                    path,
                    "Animation must contain at least one frame"
            );
        }

        return new AnimationDefinition(
                mode,
                initialDelay,
                frames
        );
    }

    private boolean hasPlacementOverride(
            ConfigurationHandle configuration,
            String path
    ) {
        return configuration.contains(
                path + ".placement"
        ) ||
                configuration.contains(
                        path + ".slot"
                ) ||
                configuration.contains(
                        path + ".slots"
                ) ||
                configuration.contains(
                        path + ".auto-equip"
                );
    }

    private ItemRenderPolicy compileRenderPolicy(
            ConfigurationHandle configuration,
            String path,
            ItemRenderPolicy fallback
    ) {
        if (
                !configuration.contains(
                        path + ".render-policy"
                )
        ) {
            return fallback;
        }

        String value =
                configuration
                        .getString(
                                path + ".render-policy",
                                fallback.name()
                        )
                        .strip()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return switch (value) {
            case "static" ->
                    ItemRenderPolicy.STATIC;

            case "dynamic" ->
                    ItemRenderPolicy.DYNAMIC;

            case "auto" ->
                    ItemRenderPolicy.AUTO;

            default ->
                    throw new ItemCompileException(
                            path + ".render-policy",
                            "Unknown render policy '"
                                    + value
                                    + "'"
                    );
        };
    }

    private long parseTicks(
            String value,
            boolean allowZero,
            String path
    ) {
        if (
                value == null ||
                        value.isBlank()
        ) {
            return allowZero
                    ? 0L
                    : 1L;
        }

        String normalized =
                value
                        .strip()
                        .toLowerCase(
                                Locale.ROOT
                        );

        try {
            double ticks;

            if (
                    normalized.endsWith("ticks")
            ) {
                ticks =
                        Double.parseDouble(
                                normalized.substring(
                                        0,
                                        normalized.length() - 5
                                )
                        );

            } else if (
                    normalized.endsWith("tick")
            ) {
                ticks =
                        Double.parseDouble(
                                normalized.substring(
                                        0,
                                        normalized.length() - 4
                                )
                        );

            } else if (
                    normalized.endsWith("t")
            ) {
                ticks =
                        Double.parseDouble(
                                normalized.substring(
                                        0,
                                        normalized.length() - 1
                                )
                        );

            } else if (
                    normalized.endsWith("ms")
            ) {
                ticks =
                        Double.parseDouble(
                                normalized.substring(
                                        0,
                                        normalized.length() - 2
                                )
                        ) / 50.0D;

            } else if (
                    normalized.endsWith("s")
            ) {
                ticks =
                        Double.parseDouble(
                                normalized.substring(
                                        0,
                                        normalized.length() - 1
                                )
                        ) * 20.0D;

            } else if (
                    normalized.endsWith("m")
            ) {
                ticks =
                        Double.parseDouble(
                                normalized.substring(
                                        0,
                                        normalized.length() - 1
                                )
                        ) * 20.0D * 60.0D;

            } else {
                ticks =
                        Double.parseDouble(
                                normalized
                        );
            }

            long result =
                    (long) Math.ceil(
                            ticks
                    );

            if (
                    allowZero &&
                            result == 0L
            ) {
                return 0L;
            }

            if (result <= 0L) {
                throw new NumberFormatException();
            }

            return result;

        } catch (
                NumberFormatException exception
        ) {
            throw new ItemCompileException(
                    path,
                    "Invalid tick duration '"
                            + value
                            + "'",
                    exception
            );
        }
    }

    private int numericOrder(
            String key
    ) {
        try {
            return Integer.parseInt(
                    key
            );

        } catch (
                NumberFormatException ignored
        ) {
            return Integer.MAX_VALUE;
        }
    }
}