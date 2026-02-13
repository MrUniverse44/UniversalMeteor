package me.blueslime.meteor.platforms.velocity.events;

import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blueslime.meteor.platforms.api.events.PlatformEvents;

import java.util.HashSet;
import java.util.Set;

public class VelocityPlatformEvents implements PlatformEvents {

    private final Set<Object> listeners = new HashSet<>();
    private final ProxyServer plugin;

    public VelocityPlatformEvents(ProxyServer plugin) {
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

        Object adapter = fetch(Object.class, "adapter");

        EventManager manager = plugin.getEventManager();

        for (Object listener : listeners) {
            manager.register(adapter, listener);
            this.listeners.add(listener);
        }
    }

    /**
     * This method will be used only to get the TypeToken<br>
     * Please ignore this method
     *
     * @return Class reference
     */
    @Override
    public Class<Object> getListenerClass() {
        return Object.class;
    }

    /**
     * Unregisters an event listener
     *
     * @param listeners to unregister
     */
    @Override
    public void unregisterListener(Object... listeners) {
        if (listeners == null || listeners.length == 0) {
            return;
        }

        Object adapter = fetch(Object.class, "adapter");

        EventManager manager = plugin.getEventManager();

        for (Object listener : listeners) {
            manager.unregisterListener(adapter, listener);
            this.listeners.remove(listener);
        }
    }

    /**
     * Unregisters all listeners
     */
    @Override
    public void unregisterAll() {
        Object adapter = fetch(Object.class, "adapter");

        EventManager manager = plugin.getEventManager();

        manager.unregisterListeners(adapter);
        this.listeners.clear();
    }

    public Set<Object> getListeners() {
        return listeners;
    }

    /**
     * Fires/calls an event
     *
     * @param event the event to fire
     */
    @Override
    public void fireEvent(Object event) {
        EventManager manager = plugin.getEventManager();

        manager.fireAndForget(event);
    }
}
