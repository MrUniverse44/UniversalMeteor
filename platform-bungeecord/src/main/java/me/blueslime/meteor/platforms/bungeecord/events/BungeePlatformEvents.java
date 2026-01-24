package me.blueslime.meteor.platforms.bungeecord.events;

import me.blueslime.meteor.platforms.api.events.PlatformEvents;
import net.md_5.bungee.api.plugin.Event;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;

import java.util.HashSet;
import java.util.Set;

public class BungeePlatformEvents implements PlatformEvents<Listener> {

    private final Set<Listener> listeners = new HashSet<>();
    private final Plugin plugin;

    public BungeePlatformEvents(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers an event listener
     *
     * @param listeners to register
     */
    @Override
    public void registerListener(Listener... listeners) {
        if (listeners == null || listeners.length == 0) {
            return;
        }

        PluginManager pm = plugin.getProxy().getPluginManager();

        for (Listener listener : listeners) {
            pm.registerListener(plugin, listener);
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
    public Class<Listener> getListenerClass() {
        return Listener.class;
    }

    /**
     * Unregisters an event listener
     *
     * @param listeners to unregister
     */
    @Override
    public void unregisterListener(Listener... listeners) {
        if (listeners == null || listeners.length == 0) {
            return;
        }
        PluginManager pm = plugin.getProxy().getPluginManager();
        for (Listener listener : listeners) {
            pm.unregisterListener(listener);
            this.listeners.remove(listener);
        }
    }

    public Set<Listener> getListeners() {
        return listeners;
    }

    /**
     * Unregisters all listeners
     */
    @Override
    public void unregisterAll() {
        PluginManager pm = plugin.getProxy().getPluginManager();
        pm.unregisterListeners(plugin);
    }

    /**
     * Fires/calls an event
     *
     * @param event the event to fire
     */
    @Override
    public void fireEvent(Object event) {
        if (event instanceof Event bungeeEvent) {
            plugin.getProxy().getPluginManager().callEvent(bungeeEvent);
        }
    }
}
