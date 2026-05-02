package me.blueslime.meteor.paper.extras.services.scoreboard.object;

import me.blueslime.meteor.implementation.Implements;
import me.blueslime.meteor.paper.extras.services.conditions.CompiledCondition;
import me.blueslime.meteor.paper.extras.services.conditions.objects.ConditionCompiler;
import me.blueslime.meteor.paper.extras.services.languages.locale.Locale;
import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import me.blueslime.meteor.platforms.api.logger.PlatformLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public record Scoreboard(String id, int priority, String title, List<String> lines, List<CompiledCondition> displayConditions) {
    public static Scoreboard of(String id, ConfigurationHandle handle) {
        List<CompiledCondition> compiled = new ArrayList<>();
        for (String raw : handle.getStringList("display-conditions")) {
            compiled.add(ConditionCompiler.compile(raw));
        }
        return new Scoreboard(
                id,
                handle.getInt("priority", 0),
                handle.getString("title", " "),
                handle.getStringList("lines"),
                compiled
        );
    }
    public static List<Scoreboard> findAllStatic(ConfigurationHandle handle) {
        List<Scoreboard> scoreboards = new ArrayList<>();
        for (String key : handle.getKeys("scoreboards", false)) {
            try {
                scoreboards.add(Scoreboard.of(key, handle.getSection("scoreboards." + key)));
            } catch (Exception e) {
                Implements.fetch(PlatformLogger.class).error(e, "Can't fetch scoreboards: " + e.getMessage());
            }
        }
        return scoreboards;
    }

    public static Map<String, List<Scoreboard>> findAllDynamic(ConfigurationHandle handle) {
        Map<String, List<Scoreboard>> scoreboards = new HashMap<>();
        for (String languageKey : handle.getKeys("scoreboards", false)) {
            final String finalLanguage = Locale.fromString(languageKey).getLanguage();
            List<Scoreboard> independent = scoreboards.computeIfAbsent(
                finalLanguage,k -> new CopyOnWriteArrayList<>()
            );

            for (String scoreboardId : handle.getKeys("scoreboards." + languageKey, false)) {
                try {
                    independent.add(Scoreboard.of(scoreboardId, handle.getSection("scoreboards." + languageKey + "." + scoreboardId)));
                    Implements.fetch(PlatformLogger.class).debug("Added Scoreboard with id " + scoreboardId + " to language code: " + finalLanguage);
                } catch (Exception e) {
                    Implements.fetch(PlatformLogger.class).error(e, "Can't fetch scoreboards: " + e.getMessage());
                }
            }
        }
        return scoreboards;
    }
}
