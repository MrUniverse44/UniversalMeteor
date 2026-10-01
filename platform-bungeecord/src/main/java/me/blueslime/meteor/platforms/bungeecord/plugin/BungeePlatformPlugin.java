package me.blueslime.meteor.platforms.bungeecord.plugin;

import me.blueslime.meteor.platforms.api.Project;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.platforms.api.info.PluginInfo;

public abstract class BungeePlatformPlugin extends PlatformPlugin {

    public BungeePlatformPlugin(PluginInfo info, Project project) {
        super(info, project);
    }

}
