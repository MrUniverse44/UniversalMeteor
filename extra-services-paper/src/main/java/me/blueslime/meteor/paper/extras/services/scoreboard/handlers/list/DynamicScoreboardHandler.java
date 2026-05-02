package me.blueslime.meteor.paper.extras.services.scoreboard.handlers.list;

import me.blueslime.meteor.paper.extras.services.languages.LanguageService;
import me.blueslime.meteor.paper.extras.services.languages.locale.Locale;
import me.blueslime.meteor.paper.extras.services.scoreboard.object.Scoreboard;
import me.blueslime.meteor.paper.extras.services.scoreboard.ScoreboardService;
import me.blueslime.meteor.paper.extras.services.scoreboard.handlers.ScoreboardHandler;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DynamicScoreboardHandler implements ScoreboardHandler {

    private final Map<String, List<Scoreboard>> localizedScoreboardList = new ConcurrentHashMap<>();

    private final ScoreboardService service;

    public DynamicScoreboardHandler(ScoreboardService service) {
        this.service = service;
    }

    @Override
    public void initialize() {
        this.localizedScoreboardList.clear();
        this.localizedScoreboardList.putAll(Scoreboard.findAllDynamic(service.getConfiguration()));
    }

    @Override
    public void reload() {
        initialize();
    }

    @Override
    public List<Scoreboard> findScoreboardsFor(Player player) {
        List<Scoreboard> possibleScoreboards = new ArrayList<>();
        LanguageService languages = getLanguages();
        Locale locale = languages.fromPlayer(player);

        List<Scoreboard> localeScoreboards = localizedScoreboardList.computeIfAbsent(locale.getLanguage(), k -> new CopyOnWriteArrayList<>());

        boolean isNotFallbackLocale = !locale.getLanguage().equalsIgnoreCase(languages.getFallbackLocale().getLanguage());
        if (isNotFallbackLocale && localeScoreboards.isEmpty()) {
            localeScoreboards = localizedScoreboardList.computeIfAbsent(languages.getFallbackLocale().getLanguage(), k -> new CopyOnWriteArrayList<>());
        }

        for (Scoreboard scoreboard : localeScoreboards) {
            if (canViewScoreboard(scoreboard.displayConditions(), player)) {
                possibleScoreboards.add(scoreboard);
            }
        }
        return possibleScoreboards;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }
}

