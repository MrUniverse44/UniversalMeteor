package me.blueslime.meteor.implementation.service;

/**
 * Registered Service is the same service but is automatically being registered<br>
 * at the Implements when is registered in the main class with the registerService method<br>
 * or you can also register it by yourself in the same class using the registerService method.
 */
public interface RegisteredService extends Service {
    default String getIdentifier() {
        return "";
    }

    default boolean hasIdentifier() {
        return false;
    }
}
