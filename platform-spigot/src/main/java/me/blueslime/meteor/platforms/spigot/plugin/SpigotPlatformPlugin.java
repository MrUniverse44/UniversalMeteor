package me.blueslime.meteor.platforms.spigot.plugin;

import me.blueslime.meteor.platforms.api.Project;
import me.blueslime.meteor.platforms.api.info.PluginInfo;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;

public abstract class SpigotPlatformPlugin extends PlatformPlugin {

    public SpigotPlatformPlugin(PluginInfo info, Project project) {
        super(info, project);
    }

}
