package me.blueslime.meteor.platforms.api.service;

import me.blueslime.meteor.implementation.Implementer;
import me.blueslime.meteor.implementation.service.Service;
import me.blueslime.meteor.platforms.api.Platforms;
import me.blueslime.meteor.platforms.api.data.PluginData;
import me.blueslime.meteor.platforms.api.logger.PlatformLogger;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.platforms.api.tasks.PlatformTasks;

public interface PlatformService extends Service, Implementer {

    default PlatformLogger getLogger() {
        return fetch(PlatformLogger.class);
    }

    default PlatformTasks getTaskScheduler() {
        return fetch(PlatformTasks.class);
    }

    default Platforms getCurrentPlatform() {
        return fetch(Platforms.class);
    }

    default PlatformPlugin getPlugin() {
        return fetch(PlatformPlugin.class);
    }

    default PluginData getPluginData() {
        return fetch(PluginData.class);
    }

}
