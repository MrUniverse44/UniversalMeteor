package me.blueslime.meteor.modules.handlers;

import me.blueslime.meteor.modules.api.api.PlatformModule;

import me.blueslime.meteor.platforms.api.logger.IPlatformLogger;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;

import java.io.File;

public abstract class UniversalPlatformModule extends PlatformModule<Object> {

    public UniversalPlatformModule(File file, PlatformPlugin platform, IPlatformLogger moduleLogger) {
        super(file, platform, moduleLogger);
    }

}
