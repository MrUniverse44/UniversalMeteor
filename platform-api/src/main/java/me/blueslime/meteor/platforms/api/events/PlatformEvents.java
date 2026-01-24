package me.blueslime.meteor.platforms.api.events;

import me.blueslime.meteor.platforms.api.service.PlatformService;
import me.blueslime.meteor.utilities.consumer.PluginConsumer;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstraction for event management across different platforms.
 * Each platform implements this to handle listener registration and event firing.
 */
public interface PlatformEvents<L> extends PlatformService {

    /**
     * Registers an event listener
     * @param listeners to register
     */
    @SuppressWarnings("unchecked")
    void registerListener(L... listeners);


    @SuppressWarnings("unchecked")
    default void registerListener(Class<? extends L>... listeners) {
        if (listeners == null || listeners.length == 0) {
            return;
        }
        List<L> convertedListeners = new ArrayList<>();
        for (Class<? extends L> listener : listeners) {
            PluginConsumer.process(
                () -> {
                    convertedListeners.add(
                        createInstance(listener)
                    );
                },
                e -> getLogger().error("Can't create instance of listener class: " + listener.getSimpleName())
            );
        }
        L[] array = (L[]) java.lang.reflect.Array.newInstance(
            getListenerClass(),
            convertedListeners.size()
        );

        registerListener(convertedListeners.toArray(array));
    }

    /**
     * This method will be used only to get the TypeToken<br>
     * Please ignore this method
     * @return Class reference
     */
    Class<L> getListenerClass();

    /**
     * Unregisters an event listener
     * @param listeners to unregister
     */
    @SuppressWarnings("unchecked")
    void unregisterListener(L... listeners);

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

