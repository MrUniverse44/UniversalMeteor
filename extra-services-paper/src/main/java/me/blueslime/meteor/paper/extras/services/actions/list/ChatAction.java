package me.blueslime.meteor.paper.extras.services.actions.list;

import me.blueslime.meteor.paper.extras.services.actions.object.Action;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.utilities.text.TextReplacer;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatAction extends Action {
    public ChatAction() {
        super("chat:", "[chat]", "<chat>");
    }

    /**
     * Execute action
     *
     * @param plugin    of the event
     * @param parameter text
     * @param players   players
     */
    @Override
    public void execute(PlatformPlugin plugin, String parameter, TextReplacer replacer, List<Player> players) {
        if (players == null || players.isEmpty()) {
            return;
        }

        parameter = replacer.apply(parameter);

        List<String> playerNames = null;

        if (parameter.contains("%for:")) {
            // well... this message will be sent for other(s) players.
            playerNames = new ArrayList<>();

            String regex = "%for:(.*?)%";

            Pattern pattern = Pattern.compile(regex);

            Matcher matcher = pattern.matcher(parameter);

            while (matcher.find()) {
                String userName = matcher.group(1);  // El contenido entre %for: y %
                playerNames.add(userName);
            }
        }

        JavaPlugin javaPlugin = plugin.to(JavaPlugin.class);

        if (playerNames == null) {

            boolean placeholders = javaPlugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");

            parameter = replace(parameter).replace("\\n", "\n");

            for (Player player : players) {

                String message = parameter;

                if (placeholders) {
                    message = PlaceholderAPI.setPlaceholders(player, message);
                }

                message = message.replace("\\n", "\n");

                player.chat(message);
            }
            return;
        }

        for (String userName : playerNames) {
            Player player = javaPlugin.getServer().getPlayer(userName);

            if (player == null) {
                continue;
            }

            boolean placeholders = javaPlugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");

            parameter = replace(parameter).replace("\\n", "\n");

            String message = parameter;

            if (placeholders) {
                message = PlaceholderAPI.setPlaceholders(player, message);
            }

            message = message.replace("\\n", "\n");

            player.chat(message);
        }
    }
}

