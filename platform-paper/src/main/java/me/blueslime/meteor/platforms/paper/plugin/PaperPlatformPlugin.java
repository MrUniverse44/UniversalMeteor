package me.blueslime.meteor.platforms.paper.plugin;

import me.blueslime.meteor.platforms.api.info.PluginInfo;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import org.bukkit.event.Listener;

public abstract class PaperPlatformPlugin extends PlatformPlugin<Listener> {

    public PaperPlatformPlugin(PluginInfo<Listener> info) {
        super(info);
    }

}
