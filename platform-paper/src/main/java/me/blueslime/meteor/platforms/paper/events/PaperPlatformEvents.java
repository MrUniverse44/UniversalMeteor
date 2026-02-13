package me.blueslime.meteor.platforms.paper.events;

import me.blueslime.meteor.platforms.api.events.PlatformEvents;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

public class PaperPlatformEvents implements PlatformEvents {

    private final Set<Listener> listeners = new HashSet<>();
    private final JavaPlugin plugin;

    public PaperPlatformEvents(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers an event listener
     *
     * @param listeners to register
     */
    @Override
    public void registerListener(Object... listeners) {
        if (listeners == null || listeners.length == 0) {
            return;
        }

        PluginManager pm = plugin.getServer().getPluginManager();

        for (Object listenerToParse : listeners) {
            if (listenerToParse instanceof Listener listener) {
                pm.registerEvents(listener, plugin);
                this.listeners.add(listener);
            } else {
                getLogger().error("Listener " + listenerToParse.getClass().getSimpleName() + " is not a valid listener");
            }
        }
    }

    /**
     * This method will be used only to get the TypeToken<br>
     * Please ignore this method
     *
     * @return Class reference
     */
    @Override
    public Class<Listener> getListenerClass() {
        return Listener.class;
    }

    /**
     * Unregisters an event listener
     *
     * @param listeners to unregister
     */
    @SuppressWarnings("RedundantLengthCheck")
    @Override
    public void unregisterListener(Object... listeners) {
        if (listeners == null || listeners.length == 0) {
            return;
        }
        for (Object listenerToParse : listeners) {
            if (listenerToParse instanceof Listener listener) {
                HandlerList.unregisterAll(listener);
                this.listeners.remove(listener);
            } else {
                getLogger().error("Listener " + listenerToParse.getClass().getSimpleName() + " is not a valid listener");
            }
        }
    }

    /**
     * Unregisters all listeners
     */
    @Override
    public void unregisterAll() {
        for (Listener listener : this.listeners) {
            HandlerList.unregisterAll(listener);
        }
    }

    /**
     * Fires/calls an event
     *
     * @param event the event to fire
     */
    @Override
    public void fireEvent(Object event) {
        if (event instanceof Event bukkitEvent) {
            plugin.getServer().getPluginManager().callEvent(bukkitEvent);
        }
    }
}
