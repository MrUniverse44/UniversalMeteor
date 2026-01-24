package me.blueslime.meteor.platforms.bungeecord.data;

import me.blueslime.meteor.platforms.api.data.PluginData;
import net.md_5.bungee.api.plugin.Plugin;

public class BungeePluginData extends PluginData {

    public BungeePluginData(Plugin plugin) {
        super(plugin.getDescription().getName(), plugin.getDescription().getAuthor().split(","), plugin.getDescription().getDescription(), plugin.getDescription().getVersion());
    }

}
