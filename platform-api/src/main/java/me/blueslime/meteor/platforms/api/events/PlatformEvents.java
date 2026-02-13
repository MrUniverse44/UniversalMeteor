package me.blueslime.meteor.platforms.api.events;

import me.blueslime.meteor.platforms.api.service.PlatformService;
import me.blueslime.meteor.utilities.consumer.PluginConsumer;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstraction for event management across different platforms.
 * Each platform implements this to handle listener registration and event firing.
 */
public interface PlatformEvents extends PlatformService {

    /**
     * Registers an event listener
     * @param listeners to register
     */
    void registerListener(Object... listeners);


    default void registerListener(Class<?>... listeners) {
        if (listeners == null || listeners.length == 0) {
            return;
        }
        List<Object> convertedListeners = new ArrayList<>();
        for (Class<?> listener : listeners) {
            PluginConsumer.process(
                () -> convertedListeners.add(
                    createInstance(listener)
                ),
                e -> getLogger().error("Can't create instance of listener class: " + listener.getSimpleName())
            );
        }

        registerListener(convertedListeners.toArray(new Object[0]));
    }

    /**
     * This method will be used only to get the TypeToken<br>
     * Please ignore this method
     * @return Class reference
     */
    Class<?> getListenerClass();

    /**
     * Unregisters an event listener
     * @param listeners to unregister
     */
    void unregisterListener(Object... listeners);

    /**
     * Unregisters all listeners
     */
    void unregisterAll();

    /**
     * Fires/calls an event
     * @param event the event to fire
     */
    void fireEvent(Object event);
}

