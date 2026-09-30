package me.blueslime.meteor.paper.extras.scoreboard.object;

import me.blueslime.meteor.implementation.Implements;

import me.blueslime.meteor.paper.extras.conditions.ConditionService;
import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionMode;
import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionPlan;

import me.blueslime.meteor.paper.extras.languages.locale.Locale;

import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import me.blueslime.meteor.platforms.api.logger.IPlatformLogger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record Scoreboard(
        String id,
        int priority,
        String title,
        List<String> lines,
        ConditionPlan displayConditions
) {

    public Scoreboard {
        Objects.requireNonNull(
                id,
                "id"
        );

        title =
                title == null
                        ? " "
                        : title;

        lines =
                lines == null
                        ? List.of()
                        : List.copyOf(
                        lines
                );

        displayConditions =
                displayConditions == null
                        ? ConditionPlan.EMPTY
                        : displayConditions;
    }

    /**
     * Compiles one scoreboard definition.
     *
     * Conditions are compiled ONCE here and are never
     * parsed again when displaying the scoreboard.
     */
    public static Scoreboard of(
            String id,
            ConfigurationHandle handle,
            ConditionService conditions
    ) {
        Objects.requireNonNull(
                handle,
                "handle"
        );

        Objects.requireNonNull(
                conditions,
                "conditions"
        );

        List<String> rawConditions =
                handle.getStringList(
                        "display-conditions"
                );

        ConditionPlan displayConditions =
                compileConditions(
                        handle,
                        rawConditions,
                        conditions
                );

        return new Scoreboard(
                id,
                handle.getInt(
                        "priority",
                        0
                ),
                handle.getString(
                        "title",
                        " "
                ),
                handle.getStringList(
                        "lines"
                ),
                displayConditions
        );
    }

    /**
     * Loads the non-localized scoreboard format:
     *
     * scoreboards:
     *   lobby:
     *     priority: 10
     *     title: "..."
     *     lines: [...]
     *     display-conditions: [...]
     */
    public static List<Scoreboard> findAllStatic(
            ConfigurationHandle handle,
            ConditionService conditions
    ) {
        Objects.requireNonNull(
                handle,
                "handle"
        );

        Objects.requireNonNull(
                conditions,
                "conditions"
        );

        List<Scoreboard> scoreboards =
                new ArrayList<>();

        IPlatformLogger logger =
                Implements.fetch(
                        IPlatformLogger.class
                );

        for (
                String key :
                handle.getKeys(
                        "scoreboards",
                        false
                )
        ) {
            String path =
                    "scoreboards."
                            + key;

            try {
                Scoreboard scoreboard =
                        Scoreboard.of(
                                key,
                                handle.getSection(
                                        path
                                ),
                                conditions
                        );

                scoreboards.add(
                        scoreboard
                );

                logger.debug(
                        "Added static scoreboard with id '"
                                + key
                                + "'"
                );

            } catch (Exception exception) {
                logger.error(
                        exception,
                        "Can't load scoreboard '"
                                + key
                                + "' from path '"
                                + path
                                + "': "
                                + exception.getMessage()
                );
            }
        }

        return List.copyOf(
                scoreboards
        );
    }

    /**
     * Loads localized scoreboard definitions:
     *
     * scoreboards:
     *   en_US:
     *     lobby:
     *       ...
     *
     *   es_ES:
     *     lobby:
     *       ...
     */
    public static Map<String, List<Scoreboard>> findAllDynamic(
            ConfigurationHandle handle,
            ConditionService conditions
    ) {
        Objects.requireNonNull(
                handle,
                "handle"
        );

        Objects.requireNonNull(
                conditions,
                "conditions"
        );

        Map<
                String,
                List<Scoreboard>
                > scoreboards =
                new LinkedHashMap<>();

        IPlatformLogger logger =
                Implements.fetch(
                        IPlatformLogger.class
                );

        for (
                String languageKey :
                handle.getKeys(
                        "scoreboards",
                        false
                )
        ) {
            String language =
                    Locale
                            .fromString(
                                    languageKey
                            )
                            .getLanguage();

            List<Scoreboard> localized =
                    new ArrayList<>();

            String languagePath =
                    "scoreboards."
                            + languageKey;

            for (
                    String scoreboardId :
                    handle.getKeys(
                            languagePath,
                            false
                    )
            ) {
                String scoreboardPath =
                        languagePath
                                + "."
                                + scoreboardId;

                try {
                    Scoreboard scoreboard =
                            Scoreboard.of(
                                    scoreboardId,
                                    handle.getSection(
                                            scoreboardPath
                                    ),
                                    conditions
                            );

                    localized.add(
                            scoreboard
                    );

                    logger.debug(
                            "Added scoreboard with id '"
                                    + scoreboardId
                                    + "' to language code '"
                                    + language
                                    + "'"
                    );

                } catch (Exception exception) {
                    logger.error(
                            exception,
                            "Can't load scoreboard '"
                                    + scoreboardId
                                    + "' for language '"
                                    + language
                                    + "' from path '"
                                    + scoreboardPath
                                    + "': "
                                    + exception.getMessage()
                    );
                }
            }

            scoreboards.put(
                    language,
                    List.copyOf(
                            localized
                    )
            );
        }

        return Map.copyOf(
                scoreboards
        );
    }

    private static ConditionPlan compileConditions(
            ConfigurationHandle handle,
            List<String> rawConditions,
            ConditionService conditions
    ) {
        if (
                rawConditions == null ||
                        rawConditions.isEmpty()
        ) {
            return ConditionPlan.EMPTY;
        }

        ConditionMode mode =
                readConditionMode(
                        handle
                );

        return conditions.compile(
                rawConditions,
                mode
        );
    }

    /**
     * Optional new configuration:
     *
     * display-conditions-mode: all
     *
     * or:
     *
     * display-conditions-mode: any
     *
     * Default remains ALL, preserving the behavior
     * of the old scoreboard condition system.
     */
    private static ConditionMode readConditionMode(
            ConfigurationHandle handle
    ) {
        String raw =
                handle.getString(
                        "display-conditions-mode",
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
                                java.util.Locale.ROOT
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
                            "Unknown display-conditions-mode '"
                                    + raw
                                    + "'"
                    );
        };
    }
}