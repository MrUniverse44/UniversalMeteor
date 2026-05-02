package me.blueslime.meteor.paper.extras.services.actions.list;

import me.blueslime.meteor.paper.extras.services.actions.object.Action;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.platforms.paper.sender.PaperSender;
import me.blueslime.meteor.utilities.text.TextReplacer;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class MessageAction extends Action {

    public MessageAction() {
        super("[message]", "<message>", "message:");
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

        JavaPlugin javaPlugin = plugin.to(JavaPlugin.class);

        boolean placeholders = javaPlugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");

        parameter = replace(parameter).replace("\\n", "\n");

        for (Player player : players) {

            String message = parameter;

            if (placeholders) {
                message = PlaceholderAPI.setPlaceholders(player, message);
            }

            PaperSender.build(player).send(message.replace("\\n", "\n"));
        }
    }
}


