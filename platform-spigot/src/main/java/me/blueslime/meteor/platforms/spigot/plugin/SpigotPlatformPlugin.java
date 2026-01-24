package me.blueslime.meteor.platforms.spigot.plugin;

import me.blueslime.meteor.platforms.api.info.PluginInfo;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import org.bukkit.event.Listener;

public abstract class SpigotPlatformPlugin extends PlatformPlugin<Listener> {

    public SpigotPlatformPlugin(PluginInfo<Listener> info) {
        super(info);
    }

}
