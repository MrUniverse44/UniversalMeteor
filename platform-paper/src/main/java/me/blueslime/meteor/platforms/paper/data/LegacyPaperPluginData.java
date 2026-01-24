package me.blueslime.meteor.platforms.paper.data;

import me.blueslime.meteor.platforms.api.data.PluginData;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("deprecation")
public class LegacyPaperPluginData extends PluginData {

    public LegacyPaperPluginData(JavaPlugin plugin) {
        super(plugin.getDescription().getName(), plugin.getDescription().getAuthors().toArray(new String[0]), plugin.getDescription().getDescription(), plugin.getDescription().getVersion());
    }

}
