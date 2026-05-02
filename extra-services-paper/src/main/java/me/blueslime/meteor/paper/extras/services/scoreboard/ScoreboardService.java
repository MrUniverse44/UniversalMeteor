package me.blueslime.meteor.paper.extras.services.scoreboard;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.score.ScoreFormat;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisplayScoreboard;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerResetScore;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerScoreboardObjective;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateScore;
import me.blueslime.meteor.color.renders.ComponentRenderer;
import me.blueslime.meteor.paper.extras.services.languages.LanguageService;
import me.blueslime.meteor.paper.extras.services.scoreboard.animations.ScoreboardAnimation;
import me.blueslime.meteor.paper.extras.services.scoreboard.animations.list.*;
import me.blueslime.meteor.paper.extras.services.scoreboard.handlers.list.DynamicScoreboardHandler;
import me.blueslime.meteor.paper.extras.services.scoreboard.handlers.ScoreboardHandler;
import me.blueslime.meteor.paper.extras.services.scoreboard.handlers.list.StaticScoreboardHandler;
import me.blueslime.meteor.paper.extras.services.scoreboard.object.Scoreboard;
import me.blueslime.meteor.paper.extras.services.scoreboard.state.PlayerScoreboardState;
import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import me.blueslime.meteor.platforms.api.service.PlatformService;
import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;
import me.blueslime.meteor.utilities.text.TextReplacer;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("SameParameterValue")
public class ScoreboardService implements PlatformService, Listener {

    private final Map<String, Function<ConfigurationHandle, ScoreboardAnimation>> animationTypes = new ConcurrentHashMap<>();
    private final Map<String, ScoreboardAnimation> loadedAnimations = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerScoreboardState> playerStates = new ConcurrentHashMap<>();

    private static final Pattern ANIMATION_PATTERN = Pattern.compile("<anim:([a-zA-Z0-9_-]+)>");
    private static final String CLIENT_OBJECTIVE_ID = "meteor_board";

    private ConfigurationHandle configuration;
    private boolean registered = false;

    private ScoreboardHandler scoreboardHandler = null;

    // * Resource data
    private String resourceName;
    private String fileName;

    public ScoreboardService() {
        this("scoreboards.yml", "/scoreboards.yml");
    }

    public ScoreboardService(String fileName, String resourceName) {
        this.resourceName = resourceName;
        this.fileName = fileName;
    }

    public void updateResource(String resourceName) {
        this.resourceName = resourceName;
    }

    public void updateFileName(String fileName) {
        this.fileName = fileName;
    }

    private void ifHandlerPresent(Consumer<ScoreboardHandler> consumer) {
        if (scoreboardHandler == null) {
            return;
        }
        consumer.accept(scoreboardHandler);
    }

    @Override
    public void initialize() {
        this.configuration = getPlugin().getConfigurationProvider().load(getFileOfDirectory(fileName), resourceName);

        final boolean alreadyRegistered = registered;

        if (!alreadyRegistered) {
            getLogger().debug("Registering scoreboard animation types and handler");
            this.registered = true;
            getEvents().registerListener(this);
            addAnimationType("ROTATE_LINES", configuration -> new RotateAnimation(configuration.getLong("update-interval-in-ms", 500), configuration.getStringList("lines")));
            addAnimationType("WRITING_IN", configuration -> new WritingInAnimation(configuration.getLong("update-interval-in-ms", 500), configuration.getString("text", "")));
            addAnimationType("WRITING_IN_AND_OUT", configuration -> new WritingInOutAnimation(configuration.getLong("update-interval-in-ms", 500), configuration.getString("text", "")));
            addAnimationType("SCROLL_LEFT", configuration -> new ScrollLeftAnimation(configuration.getLong("update-interval-in-ms", 150), configuration.getString("text", "")));
            addAnimationType("SCROLL_RIGHT", configuration -> new ScrollRightAnimation(configuration.getLong("update-interval-in-ms", 150), configuration.getString("text", "")));
            addAnimationType("SCROLL_BOUNCE", configuration -> new ScrollBounceAnimation(configuration.getLong("update-interval-in-ms", 150), configuration.getString("text", "")));
            if (isImplemented(LanguageService.class)) {
                LanguageService language = fetch(LanguageService.class);
                if (language.isStatic()) {
                    scoreboardHandler = new StaticScoreboardHandler(this);
                } else {
                    scoreboardHandler = new DynamicScoreboardHandler(this);
                }
            } else {
                scoreboardHandler = new StaticScoreboardHandler(this);
            }
            scoreboardHandler.initialize();
        }

        loadAnimationsFromConfig();

        if (!alreadyRegistered) {
            getLogger().debug("Registering scoreboard task");
            getTaskScheduler().schedule(
                () -> {
                    Collection<? extends Player> players = getPlugin().to(JavaPlugin.class).getServer().getOnlinePlayers();

                    for (Player player : players) {
                        List<Scoreboard> possibleScoreboards = scoreboardHandler.findScoreboardsFor(player);

                        if (possibleScoreboards.isEmpty()) {
                            removeCurrentBoard(player);
                            continue;
                        }

                        possibleScoreboards.sort(Comparator.comparing(Scoreboard::priority).reversed());
                        Scoreboard scoreboard = possibleScoreboards.getFirst();
                        renderBoard(player, scoreboard);
                    }
                },
                TaskOptions.builder()
                    .name("scoreboard-task")
                    .repeatDelay(50, TimeUnit.MILLISECONDS).build()
            );
        }
    }

    private void loadAnimationsFromConfig() {
        loadedAnimations.clear();
        if (configuration.contains("animations")) {
            for (String animKey : configuration.getKeys("animations", false)) {
                ConfigurationHandle animConfig = configuration.getSection("animations." + animKey);
                String type = animConfig.getString("type", "");

                Function<ConfigurationHandle, ScoreboardAnimation> factory = animationTypes.get(type.toUpperCase(Locale.ENGLISH));
                if (factory != null) {
                    loadedAnimations.put(animKey, factory.apply(animConfig));
                }
            }
        }
    }

    public void setScoreboardHandler(ScoreboardHandler scoreboardHandler) {
        this.scoreboardHandler = scoreboardHandler;
    }

    private void renderBoard(Player player, Scoreboard scoreboard) {
        TextReplacer replacer = TextReplacer.builder().replace("<player>", player.getName());

        PlayerScoreboardState state = playerStates.computeIfAbsent(player.getUniqueId(), k -> new PlayerScoreboardState());
        boolean isBrandNewToClient = state.logicalBoardId == null;
        boolean switchedLogicalBoard = !scoreboard.id().equals(state.logicalBoardId);

        String parsedTitle = applyPlaceholdersAndAnimations(player, scoreboard.title(), replacer);

        if (isBrandNewToClient) {
            sendObjectivePacket(player, CLIENT_OBJECTIVE_ID, ComponentRenderer.translate(parsedTitle), WrapperPlayServerScoreboardObjective.ObjectiveMode.CREATE);
            sendDisplayPacket(player, CLIENT_OBJECTIVE_ID);
            state.lastTitle = parsedTitle;
        }
        else if (switchedLogicalBoard || !parsedTitle.equals(state.lastTitle)) {
            sendObjectivePacket(player, CLIENT_OBJECTIVE_ID, ComponentRenderer.translate(parsedTitle), WrapperPlayServerScoreboardObjective.ObjectiveMode.UPDATE);
            state.lastTitle = parsedTitle;
        }

        state.logicalBoardId = scoreboard.id();

        int position = 15;
        Set<Integer> updatedPositions = new HashSet<>();

        for (String rawLine : scoreboard.lines()) {
            String parsedLine = applyPlaceholdersAndAnimations(player, rawLine, replacer);

            if (switchedLogicalBoard || !parsedLine.equals(state.lastLines.get(position))) {
                sendScorePacket(player, CLIENT_OBJECTIVE_ID, "line_" + position, ComponentRenderer.translate(parsedLine), position);
                state.lastLines.put(position, parsedLine);
            }
            updatedPositions.add(position);
            position--;
        }

        Iterator<Map.Entry<Integer, String>> it = state.lastLines.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, String> entry = it.next();
            if (!updatedPositions.contains(entry.getKey())) {
                removeScorePacket(player, CLIENT_OBJECTIVE_ID, "line_" + entry.getKey());
                it.remove();
            }
        }
    }

    private String applyPlaceholdersAndAnimations(Player player, String text, TextReplacer replacer) {
        if (text == null) return "";

        Matcher matcher = ANIMATION_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String animName = matcher.group(1);
            ScoreboardAnimation animation = loadedAnimations.get(animName);
            String replacement = animation != null ? animation.getCurrentFrame(replacer) : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        try {
            return PlaceholderAPI.setPlaceholders(player, result.toString());
        } catch (Exception e) {
            getLogger().error("Error parseando PAPI asíncronamente: " + e.getMessage());
            return result.toString();
        }
    }

    private void removeCurrentBoard(Player player) {
        PlayerScoreboardState state = playerStates.remove(player.getUniqueId());
        if (state != null) {
            sendObjectivePacket(player, CLIENT_OBJECTIVE_ID, Component.empty(), WrapperPlayServerScoreboardObjective.ObjectiveMode.REMOVE);
        }
    }

    private void sendObjectivePacket(Player player, String objectiveId, Component title, WrapperPlayServerScoreboardObjective.ObjectiveMode mode) {
        WrapperPlayServerScoreboardObjective packet = new WrapperPlayServerScoreboardObjective(
            objectiveId,
            mode,
            title,
            WrapperPlayServerScoreboardObjective.RenderType.INTEGER,
            ScoreFormat.blankScore()
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    @Override
    public void reload() {
        for (UUID uuid : playerStates.keySet()) {
            Player p = getPlugin().to(JavaPlugin.class).getServer().getPlayer(uuid);
            if (p != null) {
                sendObjectivePacket(p, CLIENT_OBJECTIVE_ID, Component.empty(), WrapperPlayServerScoreboardObjective.ObjectiveMode.REMOVE);
            }
        }

        playerStates.clear();
        scoreboardHandler.reload();

        initialize();
    }

    private void sendDisplayPacket(Player player, String objectiveId) {
        WrapperPlayServerDisplayScoreboard packet = new WrapperPlayServerDisplayScoreboard(
            1,
            objectiveId
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    private void sendScorePacket(Player player, String objectiveId, String scoreName, Component text, int scoreValue) {
        WrapperPlayServerUpdateScore packet = new WrapperPlayServerUpdateScore(
            scoreName,
            WrapperPlayServerUpdateScore.Action.CREATE_OR_UPDATE_ITEM,
            objectiveId,
            scoreValue,
            text,
            ScoreFormat.blankScore()
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    private void removeScorePacket(Player player, String objectiveId, String scoreName) {
        WrapperPlayServerResetScore resetPacket = new WrapperPlayServerResetScore(
            scoreName,
            objectiveId
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, resetPacket);
    }

    public void addAnimationType(String id, Function<ConfigurationHandle, ScoreboardAnimation> function) {
        animationTypes.put(id, function);
    }

    @Override
    public void shutdown() {
        playerStates.clear();
        scoreboardHandler.shutdown();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        ifHandlerPresent(handler -> handler.onConnect(event.getPlayer()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        ifHandlerPresent(handler -> handler.onDisconnect(event.getPlayer()));
        playerStates.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    public ConfigurationHandle getConfiguration() {
        return configuration;
    }
}
