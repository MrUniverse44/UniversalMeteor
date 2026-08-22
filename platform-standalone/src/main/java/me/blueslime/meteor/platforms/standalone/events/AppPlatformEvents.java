package me.blueslime.meteor.platforms.standalone.events;

import me.blueslime.meteor.platforms.api.events.PlatformEvents;
import me.blueslime.meteor.platforms.standalone.Bootstrap;

public class AppPlatformEvents implements PlatformEvents {

    public AppPlatformEvents(Bootstrap main) {

    }

    /**
     * Registers an event listener
     *
     * @param listeners to register
     */
    @Override
    public void registerListener(Object... listeners) {

    }

    /**
     * This method will be used only to get the TypeToken<br>
     * Please ignore this method
     *
     * @return Class reference
     */
    @Override
    public Class<?> getListenerClass() {
        return null;
    }

    /**
     * Unregisters an event listener
     *
     * @param listeners to unregister
     */
    @Override
    public void unregisterListener(Object... listeners) {

    }

    /**
     * Unregisters all listeners
     */
    @Override
    public void unregisterAll() {

    }

    /**
     * Fires/calls an event
     *
     * @param event the event to fire
     */
    @Override
    public void fireEvent(Object event) {

    }
}

