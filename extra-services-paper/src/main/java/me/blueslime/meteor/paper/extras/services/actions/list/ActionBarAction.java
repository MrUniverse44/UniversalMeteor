package me.blueslime.meteor.paper.extras.services.actions.list;

import me.blueslime.meteor.color.renders.ComponentRenderer;
import me.blueslime.meteor.paper.extras.services.actions.object.Action;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.utilities.text.TextReplacer;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class ActionBarAction extends Action {

    public ActionBarAction() {
        super("[actionbar]", "<actionbar>", "actionbar:");
    }

    /**
     * Execute action
     *
     * @param plugin    of the event
     * @param parameter text
     * @param replacer  of this check
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

            //if (placeholders) {
            //    message = PlaceholderAPI.setPlaceholders(player, message);
            //}

            message = message.replace("\\n", "\n");

            player.sendActionBar(ComponentRenderer.translate(message));
        }
    }

    @Override
    public boolean requiresMainThread() {
        return true;
    }
}



