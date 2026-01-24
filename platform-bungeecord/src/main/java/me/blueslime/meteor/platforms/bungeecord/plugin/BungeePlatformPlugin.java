package me.blueslime.meteor.platforms.bungeecord.plugin;

import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.platforms.api.info.PluginInfo;
import net.md_5.bungee.api.plugin.Listener;

public abstract class BungeePlatformPlugin extends PlatformPlugin<Listener> {

    public BungeePlatformPlugin(PluginInfo<Listener> info) {
        super(info);
    }

}
