package me.blueslime.meteor.platforms.api.info;

import me.blueslime.meteor.platforms.api.Platforms;
import me.blueslime.meteor.platforms.api.commands.PlatformCommands;
import me.blueslime.meteor.platforms.api.data.PluginData;
import me.blueslime.meteor.platforms.api.events.PlatformEvents;
import me.blueslime.meteor.platforms.api.logger.PlatformLogger;
import me.blueslime.meteor.platforms.api.tasks.PlatformTasks;

public class PluginInfo {

    private PlatformEvents platformEvents = null;
    private PlatformCommands commands = null;
    private PlatformLogger logger = null;
    private PluginData pluginData = null;
    private PlatformTasks tasks = null;
    private Platforms platform = null;
    private Object adapter = null;

    public void setLogger(PlatformLogger logger) {
        this.logger = logger;
    }

    public void setPlatformEvents(PlatformEvents platformEvents) {
        this.platformEvents = platformEvents;
    }

    public void setCommands(PlatformCommands commands) {
        this.commands = commands;
    }

    public void setPluginData(PluginData pluginData) {
        this.pluginData = pluginData;
    }

    public void setAdapter(Object adapter) {
        this.adapter = adapter;
    }

    public void setPlatform(Platforms platform) {
        this.platform = platform;
    }

    public void setTasks(PlatformTasks tasks) {
        this.tasks = tasks;
    }

    public PlatformEvents getPlatformEvents() {
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

    public PlatformCommands getCommands() {
        return commands;
    }

    public Object getAdapter() {
        return adapter;
    }
}
