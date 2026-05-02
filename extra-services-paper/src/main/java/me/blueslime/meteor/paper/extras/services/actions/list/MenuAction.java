package me.blueslime.meteor.paper.extras.services.actions.list;

import me.blueslime.meteor.paper.extras.services.actions.object.Action;
import me.blueslime.meteor.paper.extras.services.menus.MenuService;
import me.blueslime.meteor.paper.extras.services.menus.objects.PersonalMenu;
import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.platforms.api.service.PlatformService;
import me.blueslime.meteor.utilities.text.TextReplacer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

public class MenuAction extends Action implements PlatformService {
    public MenuAction() {
        super("[menu]", "<menu>", "menu:");
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
        parameter = replacer.apply(parameter);
        String id = replace(parameter);
        String[] split = id.replace(" ", "").split(",");

        String filename = split[0].toLowerCase(Locale.ENGLISH);
        String playerName = split.length >= 2 ? split[1] : null;

        MenuService menus = fetch(MenuService.class);

        players.forEach(player -> {
            if (playerName == null) {
                ConfigurationHandle configuration = menus.find(filename);
                if (configuration == null) {
                    getLogger().error("Menu not found for '" + filename + "'");
                    return;
                }
                getTaskScheduler().runSync(
                    () -> {
                        PersonalMenu menu = new PersonalMenu(player, configuration);
                        menu.open(player);
                    }
                );
            } else {
                Player targetPlayer = plugin.to(JavaPlugin.class).getServer().getPlayer(playerName);
                if (targetPlayer == null) {
                    getLogger().error("Player '" + playerName + "' is not online for '" + filename + "'");
                    return;
                }
                ConfigurationHandle configuration = menus.find(filename);
                if (configuration == null) {
                    getLogger().error("Menu not found for " + filename);
                    return;
                }
                getTaskScheduler().runSync(
                        () -> {
                            PersonalMenu menu = new PersonalMenu(targetPlayer, configuration);
                            menu.open(targetPlayer);
                        }
                );
            }
        });
    }
}

