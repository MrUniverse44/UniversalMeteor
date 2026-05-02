package me.blueslime.meteor.paper.extras.services.scoreboard.handlers;

import me.blueslime.meteor.paper.extras.services.conditions.CompiledCondition;
import me.blueslime.meteor.paper.extras.services.languages.LanguageService;
import me.blueslime.meteor.paper.extras.services.scoreboard.object.Scoreboard;
import me.blueslime.meteor.platforms.api.service.PlatformService;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public interface ScoreboardHandler extends PlatformService {

    /**
     * The plugin calls this event when the player leaves
     * @param player of the event.
     */
    default void onDisconnect(Player player) {

    }

    /**
     * The plugin calls this event when the player joins
     * @param player of the event.
     */
    default void onConnect(Player player) {

    }

    default List<Scoreboard> findScoreboardsFor(Player player) {
        return Collections.emptyList();
    }

    default boolean canViewScoreboard(List<CompiledCondition> conditions, Player player) {
        if (conditions == null || conditions.isEmpty()) return true;

        for (CompiledCondition condition : conditions) {
            if (!condition.test(player)) {
                return false;
            }
        }
        return true;
    }

    default LanguageService getLanguages() {
        return fetch(LanguageService.class);
    }
}
