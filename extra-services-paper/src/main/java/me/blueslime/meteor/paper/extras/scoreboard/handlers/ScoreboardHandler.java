package me.blueslime.meteor.paper.extras.scoreboard.handlers;

import me.blueslime.meteor.paper.extras.conditions.ConditionService;
import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionPlan;

import me.blueslime.meteor.paper.extras.languages.LanguageService;

import me.blueslime.meteor.paper.extras.runtime.ExecutionRuntimeService;
import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;

import me.blueslime.meteor.paper.extras.scoreboard.object.Scoreboard;

import me.blueslime.meteor.platforms.api.service.PlatformService;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public interface ScoreboardHandler
        extends PlatformService {

    default void onDisconnect(
            Player player
    ) {}

    default void onConnect(
            Player player
    ) {}

    default List<Scoreboard> findScoreboardsFor(
            Player player
    ) {
        return Collections.emptyList();
    }

    default boolean canViewScoreboard(
            ConditionPlan conditions,
            Player player
    ) {
        if (
                conditions == null ||
                        conditions.isEmpty()
        ) {
            return true;
        }

        ExecutionRuntimeService runtime =
                fetch(
                        ExecutionRuntimeService.class
                );

        ConditionService conditionService =
                fetch(
                        ConditionService.class
                );

        ExecutionContext context = runtime
            .context()
            .player(
                    player.getUniqueId(),
                    player.getName()
            )
            .variable(
                "scoreboardEvaluation",
                true
            )
            .variable(
                "scoreboard_evaluation",
                true
            )
            .build();

        /*
         * Conditions are already compiled.
         *
         * evaluateNow only executes the plan.
         * Any PAPI/Bukkit operation inside a
         * condition uses MainThreadBridge.
         */
        return conditionService
                .evaluateNow(
                        conditions,
                        context
                )
                .passed();
    }

    default LanguageService getLanguages() {
        return fetch(
                LanguageService.class
        );
    }

    default ConditionService getConditions() {
        return fetch(
                ConditionService.class
        );
    }

    default ExecutionRuntimeService getRuntime() {
        return fetch(
                ExecutionRuntimeService.class
        );
    }
}