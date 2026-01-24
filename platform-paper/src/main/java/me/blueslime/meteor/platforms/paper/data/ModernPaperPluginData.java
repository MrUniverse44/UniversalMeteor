package me.blueslime.meteor.platforms.paper.data;

import me.blueslime.meteor.platforms.api.data.PluginData;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("UnstableApiUsage")
public class ModernPaperPluginData extends PluginData {

    public ModernPaperPluginData(JavaPlugin plugin) {
        super(plugin.getPluginMeta().getName(), plugin.getPluginMeta().getAuthors().toArray(new String[0]), plugin.getPluginMeta().getDescription(), plugin.getPluginMeta().getVersion());
    }

}
