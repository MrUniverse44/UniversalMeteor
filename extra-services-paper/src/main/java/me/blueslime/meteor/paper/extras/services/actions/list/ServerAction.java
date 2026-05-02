package me.blueslime.meteor.paper.extras.services.actions.list;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import me.blueslime.meteor.paper.extras.services.actions.object.Action;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.utilities.text.TextReplacer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class ServerAction extends Action {

    public ServerAction() {
        super("server:", "[server]", "<server>");
    }

    /**
     * Execute action
     *
     * @param plugin    loader
     * @param parameter text
     * @param replacer  of the event
     * @param players   players
     */
    @Override
    public void execute(PlatformPlugin plugin, String parameter, TextReplacer replacer, List<Player> players) {
        String param = replace(parameter);

        JavaPlugin javaPlugin = plugin.to(JavaPlugin.class);

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(param);

        players.forEach(player -> player.sendPluginMessage(javaPlugin, "BungeeCord", out.toByteArray()));
    }
}

