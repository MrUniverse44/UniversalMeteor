package me.blueslime.meteor.platforms.api.info;

import me.blueslime.meteor.platforms.api.Platforms;
import me.blueslime.meteor.platforms.api.data.PluginData;
import me.blueslime.meteor.platforms.api.events.PlatformEvents;
import me.blueslime.meteor.platforms.api.logger.PlatformLogger;
import me.blueslime.meteor.platforms.api.tasks.PlatformTasks;

public class PluginInfo<L> {

    private PlatformEvents<L> platformEvents = null;
    private PlatformLogger logger = null;
    private PluginData pluginData = null;
    private PlatformTasks tasks = null;
    private Platforms platform = null;

    public void setLogger(PlatformLogger logger) {
        this.logger = logger;
    }

    public void setPlatformEvents(PlatformEvents<L> platformEvents) {
        this.platformEvents = platformEvents;
    }

    public void setPluginData(PluginData pluginData) {
        this.pluginData = pluginData;
    }

    public void setPlatform(Platforms platform) {
        this.platform = platform;
    }

    public void setTasks(PlatformTasks tasks) {
        this.tasks = tasks;
    }

    public PlatformEvents<L> getPlatformEvents() {
        return platformEvents;
    }

    public PlatformLogger getLogger() {
        return logger;
    }

    public PluginData getPluginData() {
        return pluginData;
    }

    public PlatformTasks getTasks() {
        return tasks;
    }

    public Platforms getPlatform() {
        return platform;
    }
}
