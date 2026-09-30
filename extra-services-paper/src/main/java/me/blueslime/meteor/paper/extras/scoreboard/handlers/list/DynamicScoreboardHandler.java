package me.blueslime.meteor.paper.extras.scoreboard.handlers.list;

import me.blueslime.meteor.paper.extras.languages.LanguageService;

import me.blueslime.meteor.paper.extras.languages.locale.Locale;

import me.blueslime.meteor.paper.extras.scoreboard.ScoreboardService;

import me.blueslime.meteor.paper.extras.scoreboard.handlers.ScoreboardHandler;

import me.blueslime.meteor.paper.extras.scoreboard.object.Scoreboard;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DynamicScoreboardHandler
        implements ScoreboardHandler {

    private volatile Map<
            String,
            List<Scoreboard>
            > localizedScoreboards =
            Map.of();

    private final ScoreboardService service;

    public DynamicScoreboardHandler(
            ScoreboardService service
    ) {
        this.service =
                service;
    }

    @Override
    public void initialize() {
        Map<
                String,
                List<Scoreboard>
                > loaded =
                Scoreboard.findAllDynamic(
                        service.getConfiguration(),
                        getConditions()
                );

        LinkedHashMap<
                String,
                List<Scoreboard>
                > immutable =
                new LinkedHashMap<>();

        loaded.forEach(
                (locale, scoreboards) ->
                        immutable.put(
                                normalizeLocale(
                                        locale
                                ),
                                List.copyOf(
                                        scoreboards
                                )
                        )
        );

        /*
         * Publish only after the complete registry
         * has been built.
         */
        this.localizedScoreboards =
                Map.copyOf(
                        immutable
                );
    }

    @Override
    public void reload() {
        initialize();
    }

    @Override
    public void shutdown() {
        localizedScoreboards =
                Map.of();
    }

    @Override
    public List<Scoreboard> findScoreboardsFor(
            Player player
    ) {
        Map<
                String,
                List<Scoreboard>
                > snapshot =
                localizedScoreboards;

        LanguageService languages =
                getLanguages();

        Locale locale =
                languages.fromPlayer(
                        player
                );

        String requestedLocale =
                normalizeLocale(
                        locale.getLanguage()
                );

        String fallbackLocale =
                normalizeLocale(
                        languages
                                .getFallbackLocale()
                                .getLanguage()
                );

        List<Scoreboard> candidates =
                snapshot.get(
                        requestedLocale
                );

        /*
         * es_EC -> es
         */
        if (
                candidates == null ||
                        candidates.isEmpty()
        ) {
            String base =
                    baseLocale(
                            requestedLocale
                    );

            if (base != null) {
                candidates =
                        snapshot.get(
                                base
                        );
            }
        }

        /*
         * Configured fallback.
         */
        if (
                candidates == null ||
                        candidates.isEmpty()
        ) {
            candidates =
                    snapshot.get(
                            fallbackLocale
                    );
        }

        /*
         * Fallback base.
         */
        if (
                candidates == null ||
                        candidates.isEmpty()
        ) {
            String base =
                    baseLocale(
                            fallbackLocale
                    );

            if (base != null) {
                candidates =
                        snapshot.get(
                                base
                        );
            }
        }

        if (
                candidates == null ||
                        candidates.isEmpty()
        ) {
            return List.of();
        }

        List<Scoreboard> result =
                new ArrayList<>();

        for (
                Scoreboard scoreboard :
                candidates
        ) {
            if (
                    canViewScoreboard(
                            scoreboard.displayConditions(),
                            player
                    )
            ) {
                result.add(
                        scoreboard
                );
            }
        }

        return List.copyOf(
                result
        );
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    private String normalizeLocale(
            String locale
    ) {
        return locale
                .strip()
                .toLowerCase(
                        java.util.Locale.ROOT
                )
                .replace(
                        '-',
                        '_'
                );
    }

    private String baseLocale(
            String locale
    ) {
        int index =
                locale.indexOf(
                        '_'
                );

        if (index <= 0) {
            return null;
        }

        return locale.substring(
                0,
                index
        );
    }
}