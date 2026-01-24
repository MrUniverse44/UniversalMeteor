package me.blueslime.meteor.modules.handlers;

import me.blueslime.meteor.modules.api.api.PlatformModule;

import me.blueslime.meteor.platforms.api.logger.PlatformLogger;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;

import java.io.File;

public abstract class UniversalPlatformModule extends PlatformModule<Object> {

    public UniversalPlatformModule(File file, PlatformPlugin<Object> platform, PlatformLogger moduleLogger) {
        super(file, platform, moduleLogger);
    }

}
