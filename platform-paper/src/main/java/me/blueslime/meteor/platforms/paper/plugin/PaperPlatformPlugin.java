package me.blueslime.meteor.platforms.paper.plugin;

import me.blueslime.meteor.platforms.api.Project;
import me.blueslime.meteor.platforms.api.info.PluginInfo;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;

public abstract class PaperPlatformPlugin extends PlatformPlugin {

    public PaperPlatformPlugin(PluginInfo info, Project project) {
        super(info, project);
    }

}
