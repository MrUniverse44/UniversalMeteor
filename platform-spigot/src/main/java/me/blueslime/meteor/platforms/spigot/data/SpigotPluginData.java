package me.blueslime.meteor.platforms.spigot.data;

import me.blueslime.meteor.platforms.api.data.PluginData;
import org.bukkit.plugin.java.JavaPlugin;

public class SpigotPluginData extends PluginData {

    public SpigotPluginData(JavaPlugin plugin) {
        super(plugin.getDescription().getName(), plugin.getDescription().getAuthors().toArray(new String[0]), plugin.getDescription().getDescription(), plugin.getDescription().getVersion());
    }

}
