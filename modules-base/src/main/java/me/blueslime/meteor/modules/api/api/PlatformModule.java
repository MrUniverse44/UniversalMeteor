package me.blueslime.meteor.modules.api.api;

import me.blueslime.meteor.modules.api.api.module.status.ModuleStatus;
import me.blueslime.meteor.platforms.api.logger.PlatformLogger;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;

import java.util.HashSet;
import java.util.Set;

import java.io.File;

/**
 * Base class for all dynamically loadable modules.<br>
 * Works across different platforms (Paper, Velocity, etc.) through the Platform abstraction.<br>
 * Any class that wants to be a module must extend this class.
 */
public abstract class PlatformModule<ListenerType> {

    protected final Set<ListenerType> listeners = new HashSet<>();
    protected final PlatformPlugin platform;
    protected final PlatformLogger logger;
    private final File directory;

    public PlatformModule(File file, PlatformPlugin platform, PlatformLogger moduleLogger) {
        this.directory = file;
        this.platform = platform;
        this.logger = moduleLogger;
    }

    public File getDirectory() {
        return directory;
    }

    private File checkOutput(File destiny) {
        if (!destiny.exists()) {
            if (!destiny.exists() && !destiny.mkdirs()) {
                getLogger().error("Can't create output folder: " + destiny.getName());
            }
        }
        return destiny;
    }

    /**
     * Called when the module state changes.
     * This is where you should handle module lifecycle events.
     *
     * @param status is the new status of this module
     */
    public abstract void onStatusUpdate(ModuleStatus status);

    /**
     * Gets the logger for this module
     * @return the logger
     */
    public PlatformLogger getLogger() {
        return logger;
    }

    /**
     * Gets the platform abstraction
     * @return the platform
     */
    public PlatformPlugin getPlatform() {
        return platform;
    }

    /**
     * Registers event listeners.
     * Listeners should be appropriate for the platform (Bukkit Listener for Paper, etc.)
     * @param listeners the listeners to register
     */
    @SuppressWarnings("unchecked")
    public void registerListeners(ListenerType... listeners) {
        for (ListenerType listener : listeners) {
            platform.getEvents().registerListener(listener);
            this.listeners.add(listener);
        }
    }

    /**
     * Unregisters all event listeners registered by this module
     */
    @SuppressWarnings("unchecked")
    public void unregisterListeners() {
        for (ListenerType listener : listeners) {
            platform.getEvents().unregisterListener(listener);
        }
        listeners.clear();
    }
}
