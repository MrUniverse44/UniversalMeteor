package me.blueslime.meteor.paper.extras.services.actions.list;

import me.blueslime.meteor.paper.extras.services.actions.object.Action;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.utilities.text.TextReplacer;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class PlayerCommandAction extends Action {
    public PlayerCommandAction() {
        super("[player]", "<player>", "player:");
    }

    @Override
    public void execute(PlatformPlugin plugin, String parameter, TextReplacer replacer, List<Player> players) {
        parameter = replacer.apply(parameter);

        JavaPlugin javaPlugin = plugin.to(JavaPlugin.class);

        if (players == null || players.isEmpty()) {
            return;
        }

        boolean placeholders = javaPlugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");

        for (Player player : players) {
            player.performCommand(
                placeholders ?
                    PlaceholderAPI.setPlaceholders(player, replace(parameter)) :
                    replace(parameter)
            );
        }
    }

    @Override
    public boolean requiresMainThread() {
        return true;
    }
}


