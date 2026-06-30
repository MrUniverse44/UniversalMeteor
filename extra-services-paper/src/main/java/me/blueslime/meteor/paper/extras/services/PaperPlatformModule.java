package me.blueslime.meteor.paper.extras.services;

import me.blueslime.meteor.modules.api.api.PlatformModule;
import me.blueslime.meteor.platforms.api.logger.IPlatformLogger;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import org.bukkit.event.Listener;

import java.io.File;

public abstract class PaperPlatformModule extends PlatformModule<Listener> {

    public PaperPlatformModule(File file, PlatformPlugin platform, IPlatformLogger moduleLogger) {
        super(file, platform, moduleLogger);
    }

}
