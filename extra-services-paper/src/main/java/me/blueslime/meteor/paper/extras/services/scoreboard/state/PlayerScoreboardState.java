package me.blueslime.meteor.paper.extras.services.scoreboard.state;

import java.util.HashMap;
import java.util.Map;

public class PlayerScoreboardState {
    public String logicalBoardId = null;
    public String lastTitle = null;
    public Map<Integer, String> lastLines = new HashMap<>();
}
