package me.blueslime.meteor.platforms.velocity.plugin;

import me.blueslime.meteor.platforms.api.Project;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.platforms.api.info.PluginInfo;

public abstract class VelocityPlatformPlugin extends PlatformPlugin {

    public VelocityPlatformPlugin(PluginInfo info, Project project) {
        super(info, project);
    }

}
