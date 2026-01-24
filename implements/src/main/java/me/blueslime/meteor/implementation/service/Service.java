package me.blueslime.meteor.implementation.service;

import me.blueslime.meteor.implementation.Implements;

/**
 * This is a simple service interface
 * This is used for registerService in the main class
 * to allow the usage with instance.getService method
 * with this method you can get the instance of this service
 * in other classes.
 */
public interface Service {

    default void initialize() {

    }

    default void reload() {

    }

    default void shutdown() {
        unregisterImplementedService();
    }

    /**
     * Register this service to the Implements
     */
    default void registerImplementedService() {
        Implements.register(this);
    }

    /**
     * Register this or another service to the Implements
     * @param instance to be registered
     */
    default void registerImplementedService(Object instance) {
        Implements.register(instance);
    }

    /**
     * Unregister implemented service
     */
    default void unregisterImplementedService() {
        Implements.unregister(this);
    }

    default boolean isPersistent() {
        return false;
    }
}
