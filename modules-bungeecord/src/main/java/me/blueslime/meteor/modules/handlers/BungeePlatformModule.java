package me.blueslime.meteor.modules.handlers;

import me.blueslime.meteor.modules.api.api.PlatformModule;
import me.blueslime.meteor.platforms.api.logger.PlatformLogger;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import net.md_5.bungee.api.plugin.Listener;

import java.io.File;

public abstract class BungeePlatformModule extends PlatformModule<Listener> {

    public BungeePlatformModule(File file, PlatformPlugin platform, PlatformLogger moduleLogger) {
        super(file, platform, moduleLogger);
    }

}
