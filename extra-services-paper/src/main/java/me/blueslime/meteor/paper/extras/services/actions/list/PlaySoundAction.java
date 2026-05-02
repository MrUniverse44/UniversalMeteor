package me.blueslime.meteor.paper.extras.services.actions.list;

import me.blueslime.meteor.paper.extras.services.actions.object.Action;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.utilities.text.TextReplacer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public class PlaySoundAction extends Action {
    public PlaySoundAction() {
        super("[sound]", "<sound>", "sound:");
    }

    /**
     * Execute action
     *
     * @param plugin    of the event
     * @param parameter text
     * @param players   players
     */
    @SuppressWarnings({"UnstableApiUsage", "removal"})
    @Override
    public void execute(PlatformPlugin plugin, String parameter, TextReplacer replacer, List<Player> players) {
        try {
            parameter = replacer.apply(parameter);

            String[] arguments = replace(parameter.replace(" ", "")).split(",");

            if (arguments.length == 1) {
                Sound sound = Sound.valueOf(parameter.toUpperCase(Locale.ENGLISH));

                play(players, sound, 1, 1);
            } else if (arguments.length == 2) {
                Sound sound = Sound.valueOf(arguments[0].toUpperCase(Locale.ENGLISH));

                play(players, sound, Integer.parseInt(arguments[1]), 1);
            } else if (arguments.length >= 3) {
                Sound sound = Sound.valueOf(arguments[0].toUpperCase(Locale.ENGLISH));

                play(players, sound, Integer.parseInt(arguments[1]), Integer.parseInt(arguments[2]));
            }
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().error("Can't find sound: " + parameter);
            plugin.getLogger().error("This sound can't be reproduced");
        }
    }

    private void play(List<Player> players, Sound sound, int volume, int pitch) {
        getTaskScheduler().runSync(
                () -> players.forEach(player -> player.playSound(player.getLocation(), sound, volume, pitch))
        );
    }

    @Override
    public boolean requiresMainThread() {
        return true;
    }
}

