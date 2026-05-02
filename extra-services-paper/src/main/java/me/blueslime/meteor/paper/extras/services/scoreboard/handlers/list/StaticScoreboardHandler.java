package me.blueslime.meteor.paper.extras.services.scoreboard.handlers.list;

import me.blueslime.meteor.paper.extras.services.scoreboard.object.Scoreboard;
import me.blueslime.meteor.paper.extras.services.scoreboard.ScoreboardService;
import me.blueslime.meteor.paper.extras.services.scoreboard.handlers.ScoreboardHandler;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class StaticScoreboardHandler implements ScoreboardHandler {

    private final List<Scoreboard> scoreboardList = new CopyOnWriteArrayList<>();
    private final ScoreboardService service;

    public StaticScoreboardHandler(ScoreboardService service) {
        this.service = service;
    }

    @Override
    public void initialize() {
        this.scoreboardList.clear();
        this.scoreboardList.addAll(Scoreboard.findAllStatic(service.getConfiguration()));
    }

    @Override
    public void reload() {
        initialize();
    }

    @Override
    public List<Scoreboard> findScoreboardsFor(Player player) {
        List<Scoreboard> possibleScoreboards = new ArrayList<>();
        for (Scoreboard scoreboard : scoreboardList) {
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

