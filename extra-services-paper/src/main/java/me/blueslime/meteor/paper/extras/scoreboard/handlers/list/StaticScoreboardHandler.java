package me.blueslime.meteor.paper.extras.scoreboard.handlers.list;

import me.blueslime.meteor.paper.extras.scoreboard.ScoreboardService;

import me.blueslime.meteor.paper.extras.scoreboard.handlers.ScoreboardHandler;

import me.blueslime.meteor.paper.extras.scoreboard.object.Scoreboard;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class StaticScoreboardHandler
        implements ScoreboardHandler {

    private volatile List<Scoreboard> scoreboards =
            List.of();

    private final ScoreboardService service;

    public StaticScoreboardHandler(
            ScoreboardService service
    ) {
        this.service =
                service;
    }

    @Override
    public void initialize() {
        /*
         * Build completely before publishing.
         */
        List<Scoreboard> loaded =
                Scoreboard.findAllStatic(
                        service.getConfiguration(),
                        getConditions()
                );

        this.scoreboards =
                List.copyOf(
                        loaded
                );
    }

    @Override
    public void reload() {
        initialize();
    }

    @Override
    public void shutdown() {
        scoreboards =
                List.of();
    }

    @Override
    public List<Scoreboard> findScoreboardsFor(
            Player player
    ) {
        List<Scoreboard> result =
                new ArrayList<>();

        /*
         * Local snapshot.
         *
         * Even if reload happens between these lines,
         * this invocation sees one coherent definition
         * set.
         */
        List<Scoreboard> snapshot =
                scoreboards;

        for (
                Scoreboard scoreboard :
                snapshot
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
}