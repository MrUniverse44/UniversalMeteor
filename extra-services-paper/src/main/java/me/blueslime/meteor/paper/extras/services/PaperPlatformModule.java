package me.blueslime.meteor.paper.extras.services;

import me.blueslime.meteor.modules.api.api.PlatformModule;
import me.blueslime.meteor.platforms.api.logger.PlatformLogger;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import org.bukkit.event.Listener;

import java.io.File;

public abstract class PaperPlatformModule extends PlatformModule<Listener> {

    public PaperPlatformModule(File file, PlatformPlugin platform, PlatformLogger moduleLogger) {
        super(file, platform, moduleLogger);
    }

}
